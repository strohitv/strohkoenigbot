package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsAbility;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsAbilityRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.OwnedGearAndWeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3GearDownloader implements ScheduledService {
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final S3ApiQuerySender apiQuerySender;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;
	private final Splatoon3VsGearRepository gearRepository;
	private final Splatoon3VsAbilityRepository abilityRepository;

	@Getter
	private Optional<Gears> cachedGears = Optional.empty();

	@Transactional
	public Optional<Gears> downloadGears() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var gearResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.OwnedWeaponsAndGear);

			try {
				var ownGearAndWeapons = objectMapper.readValue(gearResponse, OwnedGearAndWeaponsResult.class);

				var allHeadGears = ownGearAndWeapons.getData().getHeadGears().getNodes();
				var allClothingGears = ownGearAndWeapons.getData().getClothingGears().getNodes();
				var allShoesGears = ownGearAndWeapons.getData().getShoesGears().getNodes();

				var allGear = Stream.of(
						ownGearAndWeapons.getData().getHeadGears().getNodes(),
						ownGearAndWeapons.getData().getClothingGears().getNodes(),
						ownGearAndWeapons.getData().getShoesGears().getNodes())
					.flatMap(Arrays::stream)
					.collect(Collectors.toList());

				fillGearDataIntoDb(allGear);
				fillGearAbilitiesIntoDb(allGear);

				return Optional.of(new Gears(allHeadGears, allClothingGears, allShoesGears));
			} catch (Exception ex) {
				exceptionLogger.logExceptionAsAttachment(log, "could not refresh gears", ex);
			}
		}

		return Optional.empty();
	}

	@Transactional
	public void saveGears() {
		cachedGears = downloadGears();
	}

	@Transactional
	public void fillGearDataIntoDb(List<Gear> gears) {
		final var allUpdatedGears = new ArrayList<Splatoon3VsGear>();
		final var logBuilder = new StringBuilder("## Found gear level updates");

		for (var gear : gears) {
			gearRepository.findByName(gear.getName())
				.ifPresent(dbg -> {
					var containsChange = false;

					var oldLevel = dbg.getGearLevel();
					var newLevel = gear.getRarity();

					var oldExperience = dbg.getCurrentExperience();
					var newExperience = gear.getStats().getExp();
					var goalExperience = getGoalExp(gear);

					if (oldLevel != newLevel) {
						containsChange = true;
						dbg.setGearLevel(newLevel);
						allUpdatedGears.add(dbg);

					}

					if (oldExperience != newExperience || dbg.getGoalExperience() != goalExperience) {
						containsChange = true;
						dbg.setPreviousExperience(oldExperience);
						dbg.setCurrentExperience(newExperience);
						dbg.setGoalExperience(goalExperience);
						allUpdatedGears.add(dbg);
					}

					if (containsChange) {
						logBuilder
							.append("\n- Gear: `")
							.append(dbg.getName())
							.append("`: ");

						if (oldLevel != newLevel) {
							logBuilder.append("old level = `")
								.append(oldLevel)
								.append("`, new level = `")
								.append(newLevel)
								.append("`");
						}

						if (oldExperience != newExperience || dbg.getGoalExperience() != goalExperience) {
							if (oldLevel != newLevel) {
								logBuilder.append("; ");
							}

							logBuilder.append("old exp = `")
								.append(oldExperience)
								.append("`, new exp = `")
								.append(newExperience)
								.append("`, exp goal = `")
								.append(goalExperience)
								.append("`");
						}
					}
				});
		}

		gearRepository.saveAll(allUpdatedGears);

		if (!allUpdatedGears.isEmpty()) {
			logSender.queueLogs(log, logBuilder.toString());
		}
	}

	@Transactional
	public void fillGearAbilitiesIntoDb(List<Gear> gears) {
		final var allUpdatedGears = new ArrayList<Splatoon3VsGear>();
		final var logBuilder = new StringBuilder("## Found new abilities on gear");

		for (var gear : gears) {
			gearRepository.findByName(gear.getName())
				.ifPresent(dbg -> {
					var containsChange = false;

					var oldMain = dbg.getMainAbility();
					var newMain = abilityRepository.findByName(gear.getPrimaryGearPower().getName()).orElseThrow();

					var oldSub1 = dbg.getSubAbility1();
					var newSub1 = gear.getAdditionalGearPowers().stream()
						.findFirst()
						.flatMap(a -> abilityRepository.findByName(a.getName()))
						.orElseThrow();

					var oldSub2 = dbg.getSubAbility2();
					var newSub2 = gear.getAdditionalGearPowers().stream().skip(1)
						.findFirst()
						.flatMap(a -> abilityRepository.findByName(a.getName()))
						.orElse(null);

					var oldSub3 = dbg.getSubAbility3();
					var newSub3 = gear.getAdditionalGearPowers().stream().skip(2)
						.findFirst()
						.flatMap(a -> abilityRepository.findByName(a.getName()))
						.orElse(null);

					if (!Objects.equals(oldMain, newMain)) {
						containsChange = true;
						dbg.setMainAbility(newMain);
					}

					if (!Objects.equals(oldSub1, newSub1)) {
						containsChange = true;
						dbg.setSubAbility1(newSub1);
					}

					if (!Objects.equals(oldSub2, newSub2)) {
						containsChange = true;
						dbg.setSubAbility2(newSub2);
					}

					if (!Objects.equals(oldSub3, newSub3)) {
						containsChange = true;
						dbg.setSubAbility3(newSub3);
					}

					if (containsChange) {
						allUpdatedGears.add(dbg);

						var listEntryBuilder = new StringBuilder("\n- Gear: `")
							.append(dbg.getName())
							.append("`: ");
						var added = false;

						if (!Objects.equals(oldMain, newMain)) {
							added = true;
							listEntryBuilder
								.append("old main: `")
								.append(Optional.ofNullable(oldMain).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`, new main: `")
								.append(Optional.of(newMain).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`");
						}

						if (!Objects.equals(oldSub1, newSub1)) {
							if (added) {
								listEntryBuilder.append(", ");
							}

							added = true;
							listEntryBuilder
								.append("old sub 1: `")
								.append(Optional.ofNullable(oldSub1).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`, new sub 1: `")
								.append(Optional.of(newSub1).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`");
						}

						if (!Objects.equals(oldSub2, newSub2)) {
							if (added) {
								listEntryBuilder.append(", ");
							}

							added = true;
							listEntryBuilder
								.append("old sub 2: `")
								.append(Optional.ofNullable(oldSub2).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`, new sub 2: `")
								.append(Optional.ofNullable(newSub2).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`");
						}

						if (!Objects.equals(oldSub3, newSub3)) {
							if (added) {
								listEntryBuilder.append(", ");
							}

							listEntryBuilder
								.append("old sub 3: `")
								.append(Optional.ofNullable(oldSub3).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`, new sub 3: `")
								.append(Optional.ofNullable(newSub3).map(Splatoon3VsAbility::getName).orElse("<NULL>"))
								.append("`");
						}

						logBuilder.append(listEntryBuilder);
					}
				});
		}

		gearRepository.saveAll(allUpdatedGears);

		if (!allUpdatedGears.isEmpty()) {
			logSender.queueLogs(log, logBuilder.toString());
		}
	}

	private int getGoalExp(Gear gear) {
		if (gear.getAdditionalGearPowers().size() == 3 && !"Unknown".equalsIgnoreCase(gear.getAdditionalGearPowers().get(2).getName())) {
			return 28_000;
		} else if (gear.getAdditionalGearPowers().size() >= 2 && !"Unknown".equalsIgnoreCase(gear.getAdditionalGearPowers().get(1).getName())) {
			return 14_000;
		} else if (!gear.getAdditionalGearPowers().isEmpty() && !"Unknown".equalsIgnoreCase(gear.getAdditionalGearPowers().get(0).getName())) {
			return 8_000;
		} else {
			return 2_000;
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3GearDownloader_saveGears")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyHours(1)))
			.runnable(this::saveGears)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3GearDownloader_saveGears_initial")
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::saveGears)
			.build());
	}

	@Getter
	@AllArgsConstructor
	public static class Gears {
		private final Gear[] head;
		private final Gear[] clothing;
		private final Gear[] shoes;
	}
}

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
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.OwnedGearAndWeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

				fillLevelsIntoDb(ownGearAndWeapons.getData().getHeadGears().getNodes());
				fillLevelsIntoDb(ownGearAndWeapons.getData().getClothingGears().getNodes());
				fillLevelsIntoDb(ownGearAndWeapons.getData().getShoesGears().getNodes());

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
	public void fillLevelsIntoDb(Gear[] gears) {
		final var allUpdatedGears = new ArrayList<Splatoon3VsGear>();
		final var logBuilder = new StringBuilder("## Found gear level updates");

		for (var gear : gears) {
			gearRepository.findByName(gear.getName())
				.ifPresent(dbg -> {
					if (dbg.getGearLevel() != gear.getRarity()) {
						var oldLevel = dbg.getGearLevel();

						dbg.setGearLevel(gear.getRarity());
						allUpdatedGears.add(dbg);

						logBuilder
							.append("\n- Gear: `")
							.append(dbg.getName())
							.append("`: old level = `")
							.append(oldLevel)
							.append("`, new level = `")
							.append(dbg.getGearLevel())
							.append("`");
					}
				});
		}

		gearRepository.saveAll(allUpdatedGears);

		if (!allUpdatedGears.isEmpty()) {
			logSender.queueLogs(log, logBuilder.toString());
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

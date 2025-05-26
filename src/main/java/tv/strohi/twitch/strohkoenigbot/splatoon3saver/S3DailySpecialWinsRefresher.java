package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSpecialBadgeWins;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsSpecialBadgeWinsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsSpecialWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3DailySpecialWinsRefresher implements ScheduledService {
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsSpecialWeaponRepository specialWeaponRepository;
	private final Splatoon3VsSpecialBadgeWinsRepository specialBadgeWinsRepository;
	private final Splatoon3VsResultRepository resultRepository;

	private final DiscordBot discordBot;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("DailySpecialWinsRefresher_schedule")
			.schedule(CronSchedule.getScheduleString("0 14 * * * *"))
			.runnable(this::refreshSpecialWins)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	public void refreshSpecialWins() {
		var limitOfRefreshesPerRun = Integer.parseInt(
			configurationRepository.findByConfigName("S3DailySpecialWinsRefresher_limitOfRefreshesPerRun")
				.map(Configuration::getConfigValue)
				.orElseGet(() -> configurationRepository.save(Configuration.builder()
						.configName("S3DailySpecialWinsRefresher_limitOfRefreshesPerRun")
						.configValue("30")
						.build())
					.getConfigValue()));

		var allSpecialWeapons = specialWeaponRepository.findAll();

		var lastSavedDay = specialBadgeWinsRepository.findMaxDate()
			.orElse(LocalDate.of(2022, 9, 8));

		var today = LocalDate.now();
		var account = accountRepository.findByIsMainAccount(true).stream().findFirst();

		int numberOfDays = 0;
		while (today.isAfter((lastSavedDay = lastSavedDay.plusDays(1)))
			&& (numberOfDays = numberOfDays + 1) <= limitOfRefreshesPerRun) {
			var allSpecialWinsOfDay = resultRepository.findSpecialWinsByDateBetween(lastSavedDay.atStartOfDay().toInstant(ZoneOffset.UTC), lastSavedDay.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));

			var winsAdded = new HashMap<String, String>();

			var allDataRowsToSave = new ArrayList<Splatoon3VsSpecialBadgeWins>();
			for (var specialWeapon : allSpecialWeapons) {
				var previousDayWins = specialBadgeWinsRepository.findBySpecialWeaponIdAndStatDay(specialWeapon.getId(), lastSavedDay.minusDays(1))
					.map(Splatoon3VsSpecialBadgeWins::getWinCount)
					.orElse(0);

				var winsOfSpecialWeaponOfThisDay = allSpecialWinsOfDay.stream()
					.filter(wins -> wins.getSpecialWeapon().equals(specialWeapon))
					.findFirst()
					.map(SpecialWinCount::getWinCount)
					.orElse(0);

				var totalSpecialWinsThisDay = previousDayWins + winsOfSpecialWeaponOfThisDay;

				winsAdded.put(specialWeapon.getName(),
					String.format("%d%s", totalSpecialWinsThisDay, winsOfSpecialWeaponOfThisDay > 0 ? String.format(" (+ %d)", winsOfSpecialWeaponOfThisDay) : ""));

				allDataRowsToSave.add(new Splatoon3VsSpecialBadgeWins(specialWeapon.getId(),
					lastSavedDay,
					totalSpecialWinsThisDay,
					null));
			}

			specialBadgeWinsRepository.saveAll(allDataRowsToSave);

			sendStatsToDiscord(winsAdded, lastSavedDay, account);
		}
	}

	private void sendStatsToDiscord(Map<String, String> wins, LocalDate day, Optional<Account> account) {
		account.ifPresent(acc -> {
				var sortedStats = wins.entrySet().stream()
					.sorted((a, b) -> Integer.compare(Integer.parseInt(b.getValue().split(" ")[0]), Integer.parseInt(a.getValue().split(" ")[0])))
					.collect(Collectors.toList());
				StringBuilder statMessageBuilder = new StringBuilder("## Special wins update\nAdded **special win stats** for `")
					.append(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(day))
					.append("` to database:");

				for (var singleStat : sortedStats) {
					statMessageBuilder.append("\n- ").append(singleStat.getKey()).append(": **").append(singleStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(acc.getDiscordId(), statMessageBuilder.toString());
			}
		);
	}
}

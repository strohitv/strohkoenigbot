package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.DailyStatsSaveModel;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.EnemyResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3DailyStatsSender {
	private static final String YESTERDAY_CONFIG_NAME = "DailyStatsSender_yesterday";

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final DiscordBot discordBot;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3DailyStatsSender_schedule", CronSchedule.getScheduleString("30 25 * * * *"), this::sendStats);
	}

	private final List<Gear> allOwnedGear = new ArrayList<>();

	public void setGear(List<Gear> allGear) {
		allOwnedGear.clear();
		allOwnedGear.addAll(allGear);
	}

	private void sendStats() {
		sendStats(false);
	}

	public void sendStats(boolean force) {
		Account account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to post stats!");
			return;
		}

		if (force || LocalDateTime.now(ZoneId.of(account.getTimezone())).getHour() == 0) {
			logger.info("Start posting stats to discord");
			String accountUUIDHash = String.format("%05d", account.getId());
			sendStatsToDiscord(accountUUIDHash, account);

			logger.info("Done posting rotations to discord");
		}
	}

	public void sendStatsToDiscord(String folderName, Account account) {
		logger.info("Loading Splatoon 3 salmon run games for account with folder name '{}'...", folderName);

		Path directory = Path.of("game-results", folderName);
		if (!Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				logSender.sendLogs(logger, String.format("Could not create account directory!! %s", directory));
				return;
			}
		}

		File battleOverviewFile = directory.resolve("Already_Downloaded_Battles.json").toFile();
		ConfigFile.DownloadedGameList allDownloadedGames;
		try {
			if (battleOverviewFile.exists() && Files.size(battleOverviewFile.toPath()) > 0) { // if file already exists will do nothing
				allDownloadedGames = objectMapper.readValue(battleOverviewFile, ConfigFile.DownloadedGameList.class);
			} else if (battleOverviewFile.exists() || battleOverviewFile.createNewFile()) {
				allDownloadedGames = new ConfigFile.DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
				objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
			} else {
				logSender.sendLogs(logger, "COULD NOT OPEN SR FILE!!!");
				return;
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, "IOEXCEPTION WHILE OPENING OR WRITING OVERVIEW FILE!!!");
			logger.error(e);
			return;
		}

		DailyStatsSaveModel yesterdayStats = loadYesterdayStats();

		Map<String, Integer> wonOnlineGames = new HashMap<>();
		Map<String, Integer> winCountSpecialWeapons = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getRegular_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getX_rank_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons);
		}

		sendModeWinStatsToDiscord(wonOnlineGames, yesterdayStats, account);
		sendSpecialWeaponWinStatsToDiscord(winCountSpecialWeapons, yesterdayStats, account);

		Map<String, Integer> gearStars = new HashMap<>();
		countStarsOnGear(gearStars);
		sendGearStatsToDiscord(gearStars, yesterdayStats, account);

		Map<String, Integer> defeatedSalmonRunBosses = new HashMap<>();
		Map<String, Integer> salmonrunWeaponsYesterday = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
			countSalmonRunEnemyDefeatAndWeaponResults(game, directory, defeatedSalmonRunBosses, salmonrunWeaponsYesterday);
		}

		sendSalmonRunStatsToDiscord(defeatedSalmonRunBosses, yesterdayStats, account);

		if (salmonrunWeaponsYesterday.size() > 0) {
//			sendStatsToDiscord(defeatedSalmonRunBossesYesterday, "**Yesterday Salmon Run Boss Kill statistics:**", account);
			sendStatsToDiscord(salmonrunWeaponsYesterday, String.format("**Yesterday, you played a total of __%d__ different weapons in Salmon Run**", salmonrunWeaponsYesterday.size()), account);
		}

		refreshYesterdayStats(yesterdayStats);

		logger.info("Done with loading Splatoon 3 games for account with folder name '{}'...", folderName);
	}

	private DailyStatsSaveModel loadYesterdayStats() {
		Configuration yesterdayStatsConfig = configurationRepository.findByConfigName(YESTERDAY_CONFIG_NAME).stream().findFirst().orElse(null);

		DailyStatsSaveModel yesterdayStats = new DailyStatsSaveModel();
		if (yesterdayStatsConfig != null) {
			try {
				yesterdayStats = objectMapper.readValue(yesterdayStatsConfig.getConfigValue(), DailyStatsSaveModel.class);
			} catch (JsonProcessingException e) {
				logSender.sendLogs(logger, "yesterday stats parsing failed!!!");
				logger.error(e);
			}
		}

		return yesterdayStats;
	}

	private void refreshYesterdayStats(DailyStatsSaveModel yesterdayStats) {
		String json;
		try {
			json = objectMapper.writeValueAsString(yesterdayStats);

			Configuration config = configurationRepository.findByConfigName(YESTERDAY_CONFIG_NAME).stream().findFirst().orElse(new Configuration());
			config.setConfigName(YESTERDAY_CONFIG_NAME);
			config.setConfigValue(json);

			configurationRepository.save(config);
		} catch (JsonProcessingException e) {
			logSender.sendLogs(logger, "yesterday stats saving failed!!!");
			logger.error(e);
		}
	}

	private void sendGearStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = stats.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder winBuilder = new StringBuilder("**Current statistics about Stars on Gear:**");

		for (var gearStat : sortedStats) {
			String isFinishedChar = "o";
			if (!yesterdayStats.getIgnoredBrands().contains(gearStat.getKey())
					&& (yesterdayStats.getDoneBrands().contains(gearStat.getKey()) || gearStat.getValue() >= 30)) {
				isFinishedChar = "+";
			} else if (yesterdayStats.getIgnoredBrands().contains(gearStat.getKey())) {
				isFinishedChar = "-";
			}

			int yesterdayStarCount = yesterdayStats.getPreviousStarCount().getOrDefault(gearStat.getKey(), gearStat.getValue());

			// build message
			winBuilder.append("\n- `").append(isFinishedChar).append("` ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			if (yesterdayStarCount != gearStat.getValue()) {
				winBuilder.append(" (")
						.append(yesterdayStarCount < gearStat.getValue() ? "+" : "-")
						.append(Math.abs(yesterdayStarCount - gearStat.getValue()))
						.append(")");
			}

			// save new stats
			if (!yesterdayStats.getIgnoredBrands().contains(gearStat.getKey())
					&& !yesterdayStats.getDoneBrands().contains(gearStat.getKey())
					&& gearStat.getValue() >= 30) {
				yesterdayStats.getDoneBrands().add(gearStat.getKey());
			}

			yesterdayStats.getPreviousStarCount().put(gearStat.getKey(), gearStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), winBuilder.toString());
	}

	private void sendModeWinStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = stats.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder("**Current Online Game Win statistics:**");

		for (var singleStat : sortedStats) {
			String badgeChar = "o";
			if (yesterdayStats.getIgnoredModes().contains(singleStat.getKey())) {
				badgeChar = "-";
			} else if (singleStat.getKey().contains("Turf War") && singleStat.getValue() >= 1200) {
				badgeChar = "g";
			} else if (singleStat.getKey().contains("Turf War") && singleStat.getValue() >= 250) {
				badgeChar = "s";
			} else if (singleStat.getKey().contains("Turf War") && singleStat.getValue() >= 50) {
				badgeChar = "b";
			} else if (singleStat.getValue() >= 1000) {
				badgeChar = "g";
			} else if (singleStat.getValue() >= 100) {
				badgeChar = "s";
			}

			statMessageBuilder.append("\n- `").append(badgeChar).append("` ").append(singleStat.getKey()).append(": **").append(singleStat.getValue()).append("**");

			int yesterdayStatCount = yesterdayStats.getPreviousModeWinCount().getOrDefault(singleStat.getKey(), singleStat.getValue());

			if (yesterdayStatCount != singleStat.getValue()) {
				statMessageBuilder.append(" (")
						.append(yesterdayStatCount < singleStat.getValue() ? "+" : "-")
						.append(Math.abs(yesterdayStatCount - singleStat.getValue()))
						.append(")");
			}

			yesterdayStats.getPreviousModeWinCount().put(singleStat.getKey(), singleStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
	}

	private void sendSpecialWeaponWinStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = stats.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder("**Current Online Game Special Weapon Win statistics:**");

		for (var singleStat : sortedStats) {
			String badgeChar = "-";
			if (singleStat.getValue() >= 1200) {
				badgeChar = "g";
			} else if (singleStat.getValue() >= 180) {
				badgeChar = "s";
			} else if (singleStat.getValue() >= 30) {
				badgeChar = "p";
			}

			statMessageBuilder.append("\n- `").append(badgeChar).append("` ").append(singleStat.getKey()).append(": **").append(singleStat.getValue()).append("**");

			int yesterdaySpecialWeaponWinCount = yesterdayStats.getPreviousSpecialWeaponWinCount().getOrDefault(singleStat.getKey(), singleStat.getValue());

			if (yesterdaySpecialWeaponWinCount != singleStat.getValue()) {
				statMessageBuilder.append(" (")
						.append(yesterdaySpecialWeaponWinCount < singleStat.getValue() ? "+" : "-")
						.append(Math.abs(yesterdaySpecialWeaponWinCount - singleStat.getValue()))
						.append(")");
			}

			yesterdayStats.getPreviousSpecialWeaponWinCount().put(singleStat.getKey(), singleStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
	}

	private void sendSalmonRunStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = stats.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder("**Current Salmon Run Boss Kill statistics:**");

		for (var singleStat : sortedStats) {
			String badgeChar = "o";
			if (yesterdayStats.getIgnoredSalmonRunBosses().contains(singleStat.getKey())) {
				badgeChar = "-";
			} else if (singleStat.getValue() >= 10000) {
				badgeChar = "g";
			} else if (singleStat.getValue() >= 1000) {
				badgeChar = "s";
			} else if (singleStat.getValue() >= 100) {
				badgeChar = "b";
			}

			statMessageBuilder.append("\n- `").append(badgeChar).append("` ").append(singleStat.getKey()).append(": **").append(singleStat.getValue()).append("**");

			int yesterdaySalmonRunBossDefeatCount = yesterdayStats.getPreviousSalmonRunBossDefeatCount().getOrDefault(singleStat.getKey(), singleStat.getValue());

			if (yesterdaySalmonRunBossDefeatCount != singleStat.getValue()) {
				statMessageBuilder.append(" (")
						.append(yesterdaySalmonRunBossDefeatCount < singleStat.getValue() ? "+" : "-")
						.append(Math.abs(yesterdaySalmonRunBossDefeatCount - singleStat.getValue()))
						.append(")");
			}

			yesterdayStats.getPreviousSalmonRunBossDefeatCount().put(singleStat.getKey(), singleStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
	}

	private void sendStatsToDiscord(Map<String, Integer> stats, String header, Account account) {
		var sortedStats = stats.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder(header);

		for (var singleStat : sortedStats) {
			statMessageBuilder.append("\n- ").append(singleStat.getKey()).append(": **").append(singleStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
	}

	private void countSalmonRunEnemyDefeatAndWeaponResults(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> defeatedSalmonRunBosses, /*Map<String, Integer> defeatedSalmonRunBossesYesterday,*/ Map<String, Integer> receivedWeaponsYesterday) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);

			boolean wasToday = false;

			Instant timeAsInstant = result.getData().getCoopHistoryDetail().getPlayedTimeAsInstant();
			if (timeAsInstant != null) {
				LocalDateTime time = LocalDateTime.ofInstant(timeAsInstant, ZoneId.systemDefault());

				wasToday = time.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS))
						&& time.isBefore(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));
			} else {
				logSender.sendLogs(logger, "Instant from match was null?? WTH?");
			}

			for (EnemyResults enemyResult : result.getData().getCoopHistoryDetail().getEnemyResults()) {
				int currentCount = defeatedSalmonRunBosses.getOrDefault(enemyResult.getEnemy().getName(), 0);
				defeatedSalmonRunBosses.put(enemyResult.getEnemy().getName(), currentCount + enemyResult.getDefeatCount());

//				if (wasToday) {
//					int currentCountYesterday = defeatedSalmonRunBossesYesterday.getOrDefault(enemyResult.getEnemy().getName(), 0);
//					defeatedSalmonRunBossesYesterday.put(enemyResult.getEnemy().getName(), currentCountYesterday + enemyResult.getDefeatCount());
//				}
			}

			if (wasToday) {
				for (var weapon : result.getData().getCoopHistoryDetail().getMyResult().getWeapons()) {
					int countYesterday = receivedWeaponsYesterday.getOrDefault(weapon.getName(), 0);
					receivedWeaponsYesterday.put(weapon.getName(), countYesterday + 1);
				}
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void countStarsOnGear(Map<String, Integer> brandsWithStars) {
		for (Gear gear : allOwnedGear) {
			int currentBrandStarCount = brandsWithStars.getOrDefault(gear.getBrand().getName(), 0);
			brandsWithStars.put(gear.getBrand().getName(), currentBrandStarCount + gear.getRarity());
		}
	}

	private void countOnlineWins(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> winResults, Map<String, Integer> specialWinResults) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);

			if ("WIN".equals(result.getData().getVsHistoryDetail().getJudgement())) {
				String rule = result.getData().getVsHistoryDetail().getVsRule().getName();

				if ("Tricolor Turf War".equals(rule)) {
					if (result.getData().getVsHistoryDetail().getMyTeam().getPlayers().size() == 2) {
						rule += " (Attacker)";
					} else {
						rule += " (Defender)";
					}
				}

				int currentRuleWinCount = winResults.getOrDefault(rule, 0);
				winResults.put(rule, currentRuleWinCount + 1);

				String specialWeapon;
				if (result.getData().getVsHistoryDetail().getPlayer().getWeapon() != null) {
					specialWeapon = result.getData().getVsHistoryDetail().getPlayer().getWeapon().getSpecialWeapon().getName();
				} else {
					String id = result.getData().getVsHistoryDetail().getPlayer().getId();
					specialWeapon = result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
							.filter(pid -> pid.getId().equals(id))
							.map(p -> p.getWeapon().getSpecialWeapon().getName())
							.findFirst().orElse("UNKOWN");
				}
				int currentSpecialWinCount = specialWinResults.getOrDefault(specialWeapon, 0);
				specialWinResults.put(specialWeapon, currentSpecialWinCount + 1);
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}
}

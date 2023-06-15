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
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;
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
	private final S3Downloader downloader;
	private final S3NewGearChecker newGearChecker;
	private final S3WeaponDownloader weaponDownloader;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3DailyStatsSender_schedule", CronSchedule.getScheduleString("30 11 * * * *"), this::sendStats);
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

			downloader.downloadBattles();
			newGearChecker.checkForNewGearInSplatNetShop(true);
			weaponDownloader.loadWeapons();
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
				allDownloadedGames = new ConfigFile.DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
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
		Map<String, Integer> ownUsedWeapons = new HashMap<>();
		Map<String, Integer> ownTeamUsedWeapons = new HashMap<>();
		Map<String, Integer> enemyTeamUsedWeapons = new HashMap<>();
		Map<String, Integer> ownUsedWeaponsTotal = new HashMap<>();
		Map<String, Integer> ownTeamUsedWeaponsTotal = new HashMap<>();
		Map<String, Integer> enemyTeamUsedWeaponsTotal = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getRegular_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
				ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons, ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
				ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons, ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getX_rank_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
				ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons, ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getChallenge_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
				ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons, ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal);
		}

		sendModeWinStatsToDiscord(wonOnlineGames, yesterdayStats, account);
		sendSpecialWeaponWinStatsToDiscord(winCountSpecialWeapons, yesterdayStats, account);

		sendWeaponUsageStatsToDiscord(ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
			ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
			account);

		Map<String, Integer> gearStars = new HashMap<>();
		Map<Integer, Integer> gearStarCounts = new HashMap<>();
		countStarsOnGear(gearStars, gearStarCounts);
		sendGearStatsToDiscord(gearStars, yesterdayStats, account);
		sendGearStarCountStatsToDiscord(gearStarCounts, yesterdayStats, account);

		Map<String, Integer> weaponLevelNumbers = new HashMap<>();
		countWeaponNumberForEveryStarLevel(weaponLevelNumbers);
		sendWeaponLevelNumbersToDiscord(weaponLevelNumbers, yesterdayStats, account);

		Map<String, Integer> defeatedSalmonRunBosses = new HashMap<>();
		Map<String, Integer> salmonRunWeaponsYesterday = new HashMap<>();
		Map<String, Integer> yesterdayWaves = new HashMap<>();
		Map<String, Integer> yesterdayTides = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
			countSalmonRunEnemyDefeatAndWeaponResults(game, directory, defeatedSalmonRunBosses, salmonRunWeaponsYesterday, yesterdayWaves, yesterdayTides);
		}

		sendSalmonRunStatsToDiscord(defeatedSalmonRunBosses, yesterdayStats, account);

		if (salmonRunWeaponsYesterday.size() > 0) {
			sendStatsToDiscord(salmonRunWeaponsYesterday, String.format("**Yesterday, you played a total of __%d__ different weapons in Salmon Run**", salmonRunWeaponsYesterday.size()), account);
		}

		if (yesterdayWaves.size() > 0) {
			sendStatsToDiscord(yesterdayWaves, String.format("**Yesterday, you played a total of __%d__ different waves in Salmon Run**", yesterdayWaves.size()), account);
		}

		if (yesterdayTides.size() > 0) {
			sendStatsToDiscord(yesterdayTides, String.format("**Yesterday, you played a total of __%d__ different tides in Salmon Run**", yesterdayTides.size()), account);
		}

		refreshYesterdayStats(yesterdayStats);

		logger.info("Done with loading Splatoon 3 games for account with folder name '{}'...", folderName);
	}

	private void sendWeaponUsageStatsToDiscord(Map<String, Integer> ownUsedWeapons, Map<String, Integer> ownTeamUsedWeapons, Map<String, Integer> enemyTeamUsedWeapons,
											   Map<String, Integer> ownUsedWeaponsTotal, Map<String, Integer> ownTeamUsedWeaponsTotal, Map<String, Integer> enemyTeamUsedWeaponsTotal,
											   Account account) {
		var sortedOwnWeaponsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
		var sortedOwnTeamWeaponsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
		var sortedEnemyTeamWeaponsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
		var sortedOwnWeaponsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();
		var sortedOwnTeamWeaponsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();
		var sortedEnemyTeamWeaponsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();

		ownUsedWeapons.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedOwnWeaponsUsageStats::add);

		ownTeamUsedWeapons.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedOwnTeamWeaponsUsageStats::add);

		enemyTeamUsedWeapons.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedEnemyTeamWeaponsUsageStats::add);

		ownUsedWeaponsTotal.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedOwnWeaponsUsageStatsTotal::add);

		ownTeamUsedWeaponsTotal.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedOwnTeamWeaponsUsageStatsTotal::add);

		enemyTeamUsedWeaponsTotal.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedEnemyTeamWeaponsUsageStatsTotal::add);

		// Yesterday
		if (sortedOwnWeaponsUsageStats.size() > 0) {
			StringBuilder ownWeaponUsageBuilder = new StringBuilder("Yesterday, I used a total of **").append(sortedOwnWeaponsUsageStats.size()).append("** different weapons:");

			for (var gearStat : sortedOwnWeaponsUsageStats) {
				// build message
				ownWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownWeaponUsageBuilder.toString());

			StringBuilder ownTeamWeaponUsageBuilder = new StringBuilder("Yesterday, my teams used a total of **").append(sortedOwnTeamWeaponsUsageStats.size()).append("** different weapons:");

			for (var gearStat : sortedOwnTeamWeaponsUsageStats) {
				// build message
				ownTeamWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamWeaponUsageBuilder.toString());

			StringBuilder enemyTeamWeaponUsageBuilder = new StringBuilder("Yesterday, my enemy teams used a total of **").append(sortedEnemyTeamWeaponsUsageStats.size()).append("** different weapons:");

			for (var gearStat : sortedEnemyTeamWeaponsUsageStats) {
				// build message
				enemyTeamWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamWeaponUsageBuilder.toString());
		}

		// Total
		StringBuilder ownWeaponUsageTotalBuilder = new StringBuilder("In total, I used a total of **").append(sortedOwnWeaponsUsageStatsTotal.size()).append("** different weapons:");

		for (var gearStat : sortedOwnWeaponsUsageStatsTotal) {
			// build message
			ownWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), ownWeaponUsageTotalBuilder.toString());

		StringBuilder ownTeamWeaponUsageTotalBuilder = new StringBuilder("In total, my teams used a total of **").append(sortedOwnTeamWeaponsUsageStatsTotal.size()).append("** different weapons:");

		for (var gearStat : sortedOwnTeamWeaponsUsageStatsTotal) {
			// build message
			ownTeamWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamWeaponUsageTotalBuilder.toString());

		StringBuilder enemyTeamWeaponUsageTotalBuilder = new StringBuilder("In total, my enemy teams used a total of **").append(sortedEnemyTeamWeaponsUsageStatsTotal.size()).append("** different weapons:");

		for (var gearStat : sortedEnemyTeamWeaponsUsageStatsTotal) {
			// build message
			enemyTeamWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamWeaponUsageTotalBuilder.toString());
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

	private void sendWeaponLevelNumbersToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = new ArrayList<Map.Entry<String, Integer>>();

		stats.entrySet().stream()
			.sorted((a, b) -> orderLevelNumbersDescendingButPutLettersLast(a.getKey(), b.getKey()))
			.forEach(sortedStats::add);

		StringBuilder winBuilder = new StringBuilder("**Current statistics about Stars on Weapons:**");

		for (var gearStat : sortedStats) {
			int yesterdayStarCount = yesterdayStats.getPreviousWeaponStarsCount().getOrDefault(gearStat.getKey(), gearStat.getValue());

			// build message
			winBuilder.append("\n- ").append(gearStat.getKey()).append(" stars: **").append(gearStat.getValue()).append("**");
			if (yesterdayStarCount != gearStat.getValue()) {
				winBuilder.append(" (")
					.append(yesterdayStarCount < gearStat.getValue() ? "+" : "-")
					.append(Math.abs(yesterdayStarCount - gearStat.getValue()))
					.append(")");
			}

			yesterdayStats.getPreviousWeaponStarsCount().put(gearStat.getKey(), gearStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), winBuilder.toString());
	}

	private int orderLevelNumbersDescendingButPutLettersLast(String a, String b) {
		if (!a.matches("^\\d+$")) {
			return 1;
		}

		if (!b.matches("^\\d+$")) {
			return -1;
		}

		return String.CASE_INSENSITIVE_ORDER.compare(b, a);
	}

	private void sendGearStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = new ArrayList<Map.Entry<String, Integer>>();

		stats.entrySet().stream()
			.filter(b -> (yesterdayStats.getDoneBrands().contains(b.getKey()) || b.getValue() >= 30) && !yesterdayStats.getIgnoredBrands().contains(b.getKey()))
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedStats::add);
		stats.entrySet().stream()
			.filter(b -> !yesterdayStats.getDoneBrands().contains(b.getKey()) && b.getValue() < 30 && !yesterdayStats.getIgnoredBrands().contains(b.getKey()))
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedStats::add);
		stats.entrySet().stream()
			.filter(b -> yesterdayStats.getIgnoredBrands().contains(b.getKey()))
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(sortedStats::add);

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

	private void sendGearStarCountStatsToDiscord(Map<Integer, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = stats.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getKey(), a.getKey()))
			.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder("**Current statistics about numbers of Gear with Stars**:");

		for (var singleStat : sortedStats) {
			statMessageBuilder.append("\n- ").append(singleStat.getKey()).append(" stars: **").append(singleStat.getValue()).append("**");

			int yesterdayStatCount = yesterdayStats.getPreviousNumbersOfGearStars().getOrDefault(singleStat.getKey(), singleStat.getValue());

			if (yesterdayStatCount != singleStat.getValue()) {
				statMessageBuilder.append(" (")
					.append(yesterdayStatCount < singleStat.getValue() ? "+" : "-")
					.append(Math.abs(yesterdayStatCount - singleStat.getValue()))
					.append(")");
			}

			yesterdayStats.getPreviousNumbersOfGearStars().put(singleStat.getKey(), singleStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
	}

	private void sendModeWinStatsToDiscord(Map<String, Integer> stats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedModeWinStats = stats.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.collect(Collectors.toList());
		StringBuilder statMessageBuilder = new StringBuilder("**Current Online Game Win statistics:**");

		for (var singleStat : sortedModeWinStats) {
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

	private void countSalmonRunEnemyDefeatAndWeaponResults(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> defeatedSalmonRunBosses,
		/*Map<String, Integer> defeatedSalmonRunBossesYesterday, */ Map<String, Integer> receivedWeaponsYesterday, Map<String, Integer> waves, Map<String, Integer> tides) {
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

				for (var wave : result.getData().getCoopHistoryDetail().getWaveResults()) {
					if (wave.getEventWave() != null) {
						int countYesterday = waves.getOrDefault(wave.getEventWave().getName(), 0);
						waves.put(wave.getEventWave().getName(), countYesterday + 1);
					} else {
						String name = "No Event";
						int countYesterday = waves.getOrDefault(name, 0);
						waves.put(name, countYesterday + 1);
					}

					String tideName = "Normal Tide";
					if (wave.getWaterLevel() == 0) {
						tideName = "Low Tide";
					} else if (wave.getWaterLevel() == 2) {
						tideName = "High Tide";
					}

					int tideCountYesterday = tides.getOrDefault(tideName, 0);
					tides.put(tideName, tideCountYesterday + 1);
				}
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void countStarsOnGear(Map<String, Integer> brandsWithStars, Map<Integer, Integer> starCounts) {
		List<Gear> allOwnedGear = newGearChecker.getAllOwnedGear();
		for (Gear gear : allOwnedGear) {
			int currentBrandStarCount = brandsWithStars.getOrDefault(gear.getBrand().getName(), 0);
			brandsWithStars.put(gear.getBrand().getName(), currentBrandStarCount + gear.getRarity());

			int currentStarCount = starCounts.getOrDefault(gear.getRarity(), 0);
			starCounts.put(gear.getRarity(), currentStarCount + 1);
		}
	}

	private void countWeaponNumberForEveryStarLevel(Map<String, Integer> weaponsWithStars) {
		List<Weapon> weapons = weaponDownloader.getWeapons();
		for (Weapon weapon : weapons) {
			String levelName = "not yet obtained";

			if (weapon != null && weapon.getStats() != null && weapon.getStats().getLevel() != null) {
				levelName = String.format("%d", weapon.getStats().getLevel());
			}

			int currentWeaponNumberCount = weaponsWithStars.getOrDefault(levelName, 0);
			weaponsWithStars.put(levelName, currentWeaponNumberCount + 1);
		}
	}

	private void countOnlineWins(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> winResults, Map<String, Integer> specialWinResults,
								 Map<String, Integer> ownUsedWeapons, Map<String, Integer> ownTeamUsedWeapons, Map<String, Integer> enemyTeamUsedWeapons,
								 Map<String, Integer> ownUsedWeaponsTotal, Map<String, Integer> ownTeamUsedWeaponsTotal, Map<String, Integer> enemyTeamUsedWeaponsTotal) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);

			if (result.getData().getVsHistoryDetail().getPlayer() == null) {
				var builder = new StringBuilder("AT LEAST ONE UNEXPECTED FIELD HAS BEEN NULL!!!");

				if (result.getData().getVsHistoryDetail().getPlayer() == null) {
					builder.append("\n`result.getData().getVsHistoryDetail().getPlayer() == null`");
					result.getData().getVsHistoryDetail().setPlayer(new Player());
					result.getData().getVsHistoryDetail().getPlayer().setWeapon(new Weapon());
					result.getData().getVsHistoryDetail().getPlayer().getWeapon().setName("ERROR UNKNOWN");
				}

				discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, builder.toString());
			}

			if (result.getData().getVsHistoryDetail().getPlayer().getWeapon() == null) {
				var weapon = result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
					.filter(Player::getIsMyself)
					.findFirst()
					.map(Player::getWeapon)
					.orElse(new Weapon());

				result.getData().getVsHistoryDetail().getPlayer().setWeapon(weapon);
			}

			if (result.getData().getVsHistoryDetail().getPlayer().getResult() == null) {
				var pResult = result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
					.filter(Player::getIsMyself)
					.findFirst()
					.map(Player::getResult)
					.orElse(new PlayerResult());

				result.getData().getVsHistoryDetail().getPlayer().setResult(pResult);
			}

			var ownWeapon = result.getData().getVsHistoryDetail().getPlayer().getWeapon().getName();

			var ownTeamWeapons = result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
				.filter(p -> !p.getIsMyself())
				.map(p -> p.getWeapon().getName())
				.collect(Collectors.toList());

			var enemyTeamWeapons = result.getData().getVsHistoryDetail().getOtherTeams().stream()
				.flatMap(ot -> ot.getPlayers().stream().map(p -> p.getWeapon().getName()))
				.collect(Collectors.toList());

			mapUsedWeaponsFromGame(ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal, ownWeapon, ownTeamWeapons, enemyTeamWeapons);

			if (Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS).isBefore(result.getData().getVsHistoryDetail().getPlayedTimeAsInstant())) {
				mapUsedWeaponsFromGame(ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons, ownWeapon, ownTeamWeapons, enemyTeamWeapons);
			}

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
						.findFirst().orElse("UNKNOWN");
				}

				var player = result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
					.filter(m -> m.getIsMyself() != null && m.getIsMyself())
					.findFirst()
					.orElse(
						result.getData().getVsHistoryDetail().getPlayer().getResult() != null
							&& result.getData().getVsHistoryDetail().getPlayer().getResult().getSpecial() != null
							? result.getData().getVsHistoryDetail().getPlayer()
							: null);

				if (player != null && player.getResult().getSpecial() > 0) {
					int currentSpecialWinCount = specialWinResults.getOrDefault(specialWeapon, 0);
					specialWinResults.put(specialWeapon, currentSpecialWinCount + 1);
				}
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void mapUsedWeaponsFromGame(Map<String, Integer> ownUsedWeapons, Map<String, Integer> ownTeamUsedWeapons, Map<String, Integer> enemyTeamUsedWeapons, String ownWeapon, List<String> ownTeamWeapons, List<String> enemyTeamWeapons) {
		int currentOwnWeaponCountPreviousDay = ownUsedWeapons.getOrDefault(ownWeapon, 0);
		ownUsedWeapons.put(ownWeapon, currentOwnWeaponCountPreviousDay + 1);

		ownTeamWeapons.forEach(weapon -> {
			int currentOwnTeamWeaponCount = ownTeamUsedWeapons.getOrDefault(weapon, 0);
			ownTeamUsedWeapons.put(weapon, currentOwnTeamWeaponCount + 1);
		});

		enemyTeamWeapons.forEach(weapon -> {
			int currentEnemyTeamWeaponCount = enemyTeamUsedWeapons.getOrDefault(weapon, 0);
			enemyTeamUsedWeapons.put(weapon, currentEnemyTeamWeaponCount + 1);
		});
	}
}

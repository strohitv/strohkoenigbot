package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrResultEnemyRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.DailyStatsSaveModel;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class S3DailyStatsSender implements ScheduledService {
	private static final String YESTERDAY_CONFIG_NAME = "DailyStatsSender_yesterday";
	private static final int PAGE_SIZE = 50;

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsResultRepository vsResultRepository;

	private final Splatoon3SrResultRepository srResultRepository;
	private final Splatoon3SrResultEnemyRepository srResultEnemyRepository;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final DiscordBot discordBot;
	private final S3NewGearChecker newGearChecker;
	private final S3WeaponDownloader weaponDownloader;
	private final S3XLeaderboardDownloader xLeaderboardDownloader;

	private final S3RequestSender s3RequestSender;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3DailyStatsSender_schedule")
			.schedule(CronSchedule.getScheduleString("30 12 * * * *"))
			.runnable(this::sendStats)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	@Transactional
	public void sendStats() {
		sendStats(false);
	}

	@Transactional
	public void sendStats(boolean force) {
		Account account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to post stats!");
			return;
		}

		if (force || LocalDateTime.now(ZoneId.of(account.getTimezone())).getHour() == 0) {
			logger.info("Start posting stats to discord");
			String accountUUIDHash = String.format("%05d", account.getId());

//			downloader.downloadBattles();
			newGearChecker.checkForNewGearInSplatNetShop(true);
			weaponDownloader.loadWeapons();
			sendStatsToDiscord(accountUUIDHash, account);

			logger.info("Done posting rotations to discord");
		}
	}

	public void sendStatsToDiscord(String folderName, Account account) {
		logger.info("Loading Splatoon 3 salmon run games for account with folder name '{}'...", folderName);

		var yesterdayStats = loadYesterdayStats();

		Map<String, Integer> wonOnlineGames = new HashMap<>();
		Map<String, Integer> winCountSpecialWeapons = new HashMap<>();
		Map<String, Integer> ownUsedWeapons = new HashMap<>();
		Map<String, Integer> ownTeamUsedWeapons = new HashMap<>();
		Map<String, Integer> enemyTeamUsedWeapons = new HashMap<>();
		Map<String, Integer> ownUsedWeaponsTotal = new HashMap<>();
		Map<String, Integer> ownTeamUsedWeaponsTotal = new HashMap<>();
		Map<String, Integer> enemyTeamUsedWeaponsTotal = new HashMap<>();
		Map<String, Integer> ownUsedSpecials = new HashMap<>();
		Map<String, Integer> ownTeamUsedSpecials = new HashMap<>();
		Map<String, Integer> enemyTeamUsedSpecials = new HashMap<>();
		Map<String, Integer> ownUsedSpecialsTotal = new HashMap<>();
		Map<String, Integer> ownTeamUsedSpecialsTotal = new HashMap<>();
		Map<String, Integer> enemyTeamUsedSpecialsTotal = new HashMap<>();
		Map<String, Integer> ownUsedSpecialsPrivateBattles = new HashMap<>();
		Map<String, Integer> ownTeamUsedSpecialsPrivateBattles = new HashMap<>();
		Map<String, Integer> enemyTeamUsedSpecialsPrivateBattles = new HashMap<>();

		Map<String, Integer> gearStars = new HashMap<>();
		Map<Integer, Integer> gearStarCounts = new HashMap<>();
		Map<String, Map<Integer, Integer>> gearStarCountPerBrand = new HashMap<>();
		countStarsOnGear(gearStars, gearStarCounts, gearStarCountPerBrand);

		Map<String, Integer> weaponLevelNumbers = new HashMap<>();
		countWeaponNumberForEveryStarLevel(weaponLevelNumbers);

		Map<String, Integer> weaponExpTierNumbers = new HashMap<>();
		countWeaponNumbersForEveryExpTier(weaponExpTierNumbers);

		Map<String, Integer> defeatedSalmonRunBosses = new HashMap<>();
		Map<String, Integer> salmonRunWeaponsYesterday = new HashMap<>();
		Map<String, Integer> yesterdayWaves = new HashMap<>();
		Map<String, Integer> yesterdayTides = new HashMap<>();

		var responseCodes = s3RequestSender.getResponseCodes();
		var responseCodeMessage = responseCodes.entrySet().stream()
			.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
			.map(e -> String.format("- **%s**: %s times", e.getKey(), e.getValue()))
			.reduce((a, b) -> String.format("%s\n%s", a, b))
			.orElse("- **no calls** to the api were detected!");
		discordBot.sendPrivateMessage(account.getDiscordId(), String.format("These response codes were retrieved from SplatNet:\n%s", responseCodeMessage));

		var useNewWay = configurationRepository.findAllByConfigName("s3UseDatabase").stream()
			.map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
			.findFirst()
			.orElse(false);

		if (useNewWay) {
			countOnlineWins(wonOnlineGames, winCountSpecialWeapons);
			countSalmonRunStatistics(defeatedSalmonRunBosses, salmonRunWeaponsYesterday, yesterdayWaves, yesterdayTides);
		} else {
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

			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getRegular_games().entrySet()) {
				countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
					ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
					ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
					ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials, ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal);
			}
			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
				countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
					ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
					ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
					ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials, ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal);
			}
			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getX_rank_games().entrySet()) {
				countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
					ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
					ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
					ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials, ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal);
			}
			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getChallenge_games().entrySet()) {
				countOnlineWins(game, directory, wonOnlineGames, winCountSpecialWeapons,
					ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
					ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
					ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials, ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal);
			}
			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getPrivate_games().entrySet()) {
				countOnlineWins(game, directory, new HashMap<>(), new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(),
					ownUsedSpecialsPrivateBattles, ownTeamUsedSpecialsPrivateBattles, enemyTeamUsedSpecialsPrivateBattles,
					new HashMap<>(), new HashMap<>(), new HashMap<>());
			}

			for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
				countSalmonRunEnemyDefeatAndWeaponResults(game, directory, defeatedSalmonRunBosses, salmonRunWeaponsYesterday, yesterdayWaves, yesterdayTides);
			}
		}

		var minTop500XPowers = xLeaderboardDownloader.loadTop500MinPower();

		var allWeaponsBelow4Stars = weaponDownloader.getWeapons().stream().filter(w -> w.getStats().getLevel() < 4).collect(Collectors.toList());
		var requiredExpFor4StarGrind = getExpNeededFor4StarGrind(allWeaponsBelow4Stars);

		sendModeWinStatsToDiscord(wonOnlineGames, yesterdayStats, account);
		sendSpecialWeaponWinStatsToDiscord(winCountSpecialWeapons, yesterdayStats, account);

		sendGearStatsToDiscord(gearStars, gearStarCountPerBrand, yesterdayStats, account);
		sendGearStarCountStatsToDiscord(gearStarCounts, yesterdayStats, account);

		sendWeaponLevelNumbersToDiscord(weaponLevelNumbers, yesterdayStats, account);
		sendWeaponExpNumbersToDiscord(weaponExpTierNumbers, yesterdayStats, account);
		sendRequiredExpFor4StarGrindToDiscord(requiredExpFor4StarGrind, yesterdayStats, allWeaponsBelow4Stars, account);

		sendMinTop500XPowersToDiscord(minTop500XPowers, yesterdayStats, account);

		sendSalmonRunStatsToDiscord(defeatedSalmonRunBosses, yesterdayStats, account);

		if (!salmonRunWeaponsYesterday.isEmpty()) {
			sendStatsToDiscord(salmonRunWeaponsYesterday, String.format("**Yesterday, you played a total of __%d__ different weapons in Salmon Run**", salmonRunWeaponsYesterday.size()), account);
		}

		if (!yesterdayWaves.isEmpty()) {
			sendStatsToDiscord(yesterdayWaves, String.format("**Yesterday, you played a total of __%d__ different waves in Salmon Run**", yesterdayWaves.size()), account);
		}

		if (!yesterdayTides.isEmpty()) {
			sendStatsToDiscord(yesterdayTides, String.format("**Yesterday, you played a total of __%d__ different tides in Salmon Run**", yesterdayTides.size()), account);
		}

		sendSpecialWeaponCountStatsToDiscord(ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials,
			ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal,
			ownUsedSpecialsPrivateBattles, ownTeamUsedSpecialsPrivateBattles, enemyTeamUsedSpecialsPrivateBattles,
			account);

		sendWeaponUsageStatsToDiscord(ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
			ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
			account);

		refreshYesterdayStats(yesterdayStats);

		logger.info("Done with loading Splatoon 3 games for account with folder name '{}'...", folderName);
	}

	private void countWeaponNumbersForEveryExpTier(Map<String, Integer> weaponExpNumbers) {
		var formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
		var symbols = formatter.getDecimalFormatSymbols();

		symbols.setGroupingSeparator(' ');
		formatter.setDecimalFormatSymbols(symbols);

		List<Weapon> weapons = weaponDownloader.getWeapons();
		for (Weapon weapon : weapons) {
			String tierName = "Max Exp";

			if (weapon.getStats() == null || weapon.getStats().getLevel() == null) {
				// not purchased yet
				tierName = "Not purchased yet";
			} else if (weapon.getStats().getLevel() < 5) {
				var currentExpLowerBound = getCurrentExpLowerBound(weapon);
				var currentExpUpperBound = currentExpLowerBound + 10_000;

				var lowerBound = String.format("%s%s", formatter.format(currentExpLowerBound / 1_000), currentExpLowerBound > 0 ? "k" : "");
				var upperBound = formatter.format(currentExpUpperBound / 1_000);

				tierName = String.format("%s - %sk", lowerBound, upperBound);
			}

			int currentWeaponNumberCount = weaponExpNumbers.getOrDefault(tierName, 0);
			weaponExpNumbers.put(tierName, currentWeaponNumberCount + 1);
		}
	}

	private static int getCurrentExpLowerBound(Weapon weapon) {
		int nextExpGoal;

		switch (weapon.getStats().getLevel()) {
			case 0:
				nextExpGoal = 5_000;
				break;
			case 1:
				nextExpGoal = 25_000;
				break;
			case 2:
				nextExpGoal = 60_000;
				break;
			case 3:
				nextExpGoal = 160_000;
				break;
			case 4:
			default:
				nextExpGoal = 1_160_000;
				break;
		}

		var currentWeaponExp = nextExpGoal - weapon.getStats().getExpToLevelUp();
		return currentWeaponExp - (currentWeaponExp % 10_000);
	}

	private void sendRequiredExpFor4StarGrindToDiscord(int requiredExpFor4StarGrind, DailyStatsSaveModel yesterdayStats, List<Weapon> unfinishedWeapons, Account account) {
		var df = new DecimalFormat("#,###,###");
		var yesterdayExpRequired = yesterdayStats.getPreviousRequiredExpFor4StarGrind() != null
			? yesterdayStats.getPreviousRequiredExpFor4StarGrind()
			: requiredExpFor4StarGrind;

		StringBuilder expBuilder = new StringBuilder("**Current amount of weapon exp points required to finish 4 star grind:**\n- **")
			.append(df.format(requiredExpFor4StarGrind).replaceAll(",", " "))
			.append("** exp");

		if (!Objects.equals(yesterdayExpRequired, requiredExpFor4StarGrind)) {
			expBuilder.append(" (")
				.append(yesterdayExpRequired < requiredExpFor4StarGrind ? "+" : "-")
				.append(df.format(Math.abs(yesterdayExpRequired - requiredExpFor4StarGrind)).replaceAll(",", " "))
				.append(")");
		}

		var requiredKoWinsFor4StarGrind = requiredExpFor4StarGrind / 2_500 + 1;
		var yesterdayKoWins = yesterdayStats.getPreviousRequiredExpFor4StarGrind() != null
			? yesterdayStats.getPreviousRequiredExpFor4StarGrind() / 2_500 + 1
			: requiredExpFor4StarGrind / 2_500 + 1;

		expBuilder.append("\n- = **").append(requiredKoWinsFor4StarGrind).append("** knockout wins");

		if (!Objects.equals(yesterdayKoWins, requiredKoWinsFor4StarGrind)) {
			expBuilder.append(" (")
				.append(yesterdayKoWins < requiredKoWinsFor4StarGrind ? "+" : "-")
				.append(df.format(Math.abs(yesterdayKoWins - requiredKoWinsFor4StarGrind)).replaceAll(",", " "))
				.append(")");
		}

		expBuilder.append("\n- I will need roughly **").append(requiredExpFor4StarGrind / 50_000 + 1).append(" days** if I farm 50k exp every day.");

		var todayAverage = 160_000 - (requiredExpFor4StarGrind / unfinishedWeapons.size() + 1);
		expBuilder.append("\n- On average, I have  **").append(df.format(todayAverage).replaceAll(",", " ")).append(" exp** on every remaining weapon");

		var yesterdayUnfinishedCount = yesterdayStats.getPreviousWeaponStarsCount().keySet().stream()
			.filter(k -> !k.contains("4") && !k.contains("5"))
			.map((a) -> yesterdayStats.getPreviousWeaponStarsCount().get(a))
			.reduce(Integer::sum)
			.orElse(143);
		var yesterdayAverage = 160_000 - (yesterdayExpRequired / yesterdayUnfinishedCount + 1);

		if (todayAverage != yesterdayAverage) {
			expBuilder.append(" (")
				.append(yesterdayAverage < todayAverage ? "+" : "-")
				.append(df.format(Math.abs(yesterdayAverage - todayAverage)).replaceAll(",", " "))
				.append(")");
		}

		yesterdayStats.setPreviousRequiredExpFor4StarGrind(requiredExpFor4StarGrind);

		discordBot.sendPrivateMessage(account.getDiscordId(), expBuilder.toString());
	}

	private int getExpNeededFor4StarGrind(List<Weapon> unfinishedWeapons) {
		int requiredExp = 0;

		for (var weapon : unfinishedWeapons) {
			if (weapon.getStats() == null || weapon.getStats().getLevel() == null) {
				// not purchased yet
				requiredExp += 160_000;
				continue;
			}

			var requiredExpForWeapon = weapon.getStats().getExpToLevelUp();
			if (weapon.getStats().getLevel() < 3) {
				requiredExpForWeapon += 100_000;
			}
			if (weapon.getStats().getLevel() < 2) {
				requiredExpForWeapon += 35_000;
			}
			if (weapon.getStats().getLevel() < 1) {
				requiredExpForWeapon += 20_000;
			}

			requiredExp += requiredExpForWeapon;
		}

		return requiredExp;
	}

	private void sendMinTop500XPowersToDiscord(Map<String, Double> minTop500XPowers, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = Stream.of(minTop500XPowers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("ar")).findFirst().orElse(null),
				minTop500XPowers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("lf")).findFirst().orElse(null),
				minTop500XPowers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("gl")).findFirst().orElse(null),
				minTop500XPowers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("cl")).findFirst().orElse(null))
			.filter(Objects::nonNull)
			.collect(Collectors.toList());

		StringBuilder thresholdBuilder = new StringBuilder("**Current X powers required to get top 500:**");

		for (var thresholdStat : sortedStats) {
			var yesterdayTop500Threshold = yesterdayStats.getPreviousXRankTop500Thresholds().getOrDefault(thresholdStat.getKey(), thresholdStat.getValue());

			// build message
			thresholdBuilder.append("\n- ").append(getRuleName(thresholdStat.getKey())).append(": **").append(thresholdStat.getValue()).append("**");
			if (!Objects.equals(yesterdayTop500Threshold, thresholdStat.getValue())) {
				thresholdBuilder.append(" (")
					.append(yesterdayTop500Threshold < thresholdStat.getValue() ? "+" : "-")
					.append(String.format("%.1f", Math.abs(yesterdayTop500Threshold - thresholdStat.getValue())))
					.append(")");
			}

			yesterdayStats.getPreviousXRankTop500Thresholds().put(thresholdStat.getKey(), thresholdStat.getValue());
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), thresholdBuilder.toString());
	}

	private String getRuleName(String key) {
		if (key.equalsIgnoreCase("ar")) {
			return "Zones";
		} else if (key.equalsIgnoreCase("lf")) {
			return "Tower";
		} else if (key.equalsIgnoreCase("gl")) {
			return "Rainmaker";
		} else {
			return "Clams";
		}
	}

	private void countOnlineWins(Map<String, Integer> ruleWins, Map<String, Integer> specialWinResults) {
		var modeAndRuleWins = vsResultRepository.findModeAndRuleWinCounts();

		modeAndRuleWins.forEach(mrw -> {
			var rule = mrw.getRule().getName();

			if ("TRI_COLOR".equals(mrw.getRule().getApiRule())) {
				var allTriColorOwnWinTeams = vsResultRepository.findTriColorOwnTeamWins(mrw.getMode());

				var sortedByTeamSize = allTriColorOwnWinTeams.stream().collect(Collectors.groupingBy(dings ->
					dings.getPlayerSize() == 2
						? "Tricolor Turf War (Attacker)"
						: "Tricolor Turf War (Defender)"));

				sortedByTeamSize.forEach((name, list) -> ruleWins.put(name, list.size()));
			} else {
				int currentRuleWinCount = ruleWins.getOrDefault(rule, 0);
				ruleWins.put(rule, currentRuleWinCount + mrw.getWinCount());
			}
		});

		var specialWeaponWins = vsResultRepository.findSpecialWins();
		specialWeaponWins.forEach(win -> {
			int currentSpecialWinCount = specialWinResults.getOrDefault(win.getSpecialWeapon().getName(), 0);
			specialWinResults.put(win.getSpecialWeapon().getName(), currentSpecialWinCount + win.getWinCount());
		});
	}

	private void countSalmonRunStatistics(Map<String, Integer> defeatedSalmonRunBosses, Map<String, Integer> salmonRunWeaponsYesterday, Map<String, Integer> yesterdayWaves, Map<String, Integer> yesterdayTides) {
		var enemyDestroyStats = srResultEnemyRepository.findOwnDestroySumGroupByEnemyId();
		for (var enemyStats : enemyDestroyStats) {
			defeatedSalmonRunBosses.put(enemyStats.getName(), enemyStats.getOwnDestroyCount());
		}

		var gamesPageable = Pageable.ofSize(PAGE_SIZE);
		Page<Splatoon3SrResult> games;

		while ((games = srResultRepository.findAllByPlayedTimeGreaterThanEqual(LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant(), gamesPageable)).hasContent()) {
			for (var game : games) {
				var ownPlayerWeapons = game.getWaves().stream()
					.flatMap(w -> w.getPlayerWeapons().stream().filter(pw -> pw.getPlayer().isMyself()))
					.collect(Collectors.toList());

				for (var playerWeapon : ownPlayerWeapons) {
					int countYesterday = salmonRunWeaponsYesterday.getOrDefault(playerWeapon.getWeapon().getName(), 0);
					salmonRunWeaponsYesterday.put(playerWeapon.getWeapon().getName(), countYesterday + 1);
				}

				for (var wave : game.getWaves()) {
					if (wave.getEventWave() != null) {
						int countYesterday = yesterdayWaves.getOrDefault(wave.getEventWave().getName(), 0);
						yesterdayWaves.put(wave.getEventWave().getName(), countYesterday + 1);
					} else {
						String name = "No Event";
						int countYesterday = yesterdayWaves.getOrDefault(name, 0);
						yesterdayWaves.put(name, countYesterday + 1);
					}

					String tideName = "Normal Tide";
					if (wave.getWaterLevel() == 0) {
						tideName = "Low Tide";
					} else if (wave.getWaterLevel() == 2) {
						tideName = "High Tide";
					}

					int tideCountYesterday = yesterdayTides.getOrDefault(tideName, 0);
					yesterdayTides.put(tideName, tideCountYesterday + 1);
				}
			}

			if (games.isLast()) {
				break;
			}

			gamesPageable = games.nextPageable();
			logger.info("sr game pageable now at {}", gamesPageable.getOffset());
		}
	}

	private void sendWeaponUsageStatsToDiscord(Map<String, Integer> ownUsedWeapons, Map<String, Integer> ownTeamUsedWeapons, Map<String, Integer> enemyTeamUsedWeapons,
											   Map<String, Integer> ownUsedWeaponsTotal, Map<String, Integer> ownTeamUsedWeaponsTotal, Map<String, Integer> enemyTeamUsedWeaponsTotal,
											   Account account) {
		var maxLimitConfig = configurationRepository.findAllByConfigName("s3DailyStatsMaxLimitWeapons").stream()
			.findFirst()
			.orElseGet(() -> configurationRepository.save(Configuration.builder().configName("s3DailyStatsMaxLimitWeapons").configValue("15").build()));
		var includeWeaponsConfig = configurationRepository.findAllByConfigName("s3DailyStatsIncludeWeapons").stream()
			.findFirst()
			.orElseGet(() -> configurationRepository.save(Configuration.builder().configName("s3DailyStatsIncludeWeapons").configValue("false").build()));

		if ("true".equalsIgnoreCase(includeWeaponsConfig.getConfigValue())) {
			configurationRepository.save(includeWeaponsConfig.toBuilder().configValue("false").build());
			int maxLimit = Integer.parseInt(maxLimitConfig.getConfigValue());

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
			if (!sortedOwnWeaponsUsageStats.isEmpty()) {
				StringBuilder ownWeaponUsageBuilder = new StringBuilder("Yesterday, I used a total of **").append(sortedOwnWeaponsUsageStats.size()).append("** different weapons:");

				for (var gearStat : sortedOwnWeaponsUsageStats) {
					// build message
					ownWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownWeaponUsageBuilder.toString());

				StringBuilder ownTeamWeaponUsageBuilder = new StringBuilder("Yesterday, my teams used a total of **").append(sortedOwnTeamWeaponsUsageStats.size()).append("** different weapons:");

				var list = sortedOwnTeamWeaponsUsageStats.stream().limit(maxLimit).collect(Collectors.toList());
				for (var gearStat : list) {
					// build message
					ownTeamWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamWeaponUsageBuilder.toString());

				StringBuilder enemyTeamWeaponUsageBuilder = new StringBuilder("Yesterday, my enemy teams used a total of **").append(sortedEnemyTeamWeaponsUsageStats.size()).append("** different weapons:");

				list = sortedEnemyTeamWeaponsUsageStats.stream().limit(maxLimit).collect(Collectors.toList());
				for (var gearStat : list) {
					// build message
					enemyTeamWeaponUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamWeaponUsageBuilder.toString());
			}

			// Total
			StringBuilder ownWeaponUsageTotalBuilder = new StringBuilder("In total, I used a total of **").append(sortedOwnWeaponsUsageStatsTotal.size()).append("** different weapons:");

			var list = sortedOwnWeaponsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				ownWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownWeaponUsageTotalBuilder.toString());

			StringBuilder ownTeamWeaponUsageTotalBuilder = new StringBuilder("In total, my teams used a total of **").append(sortedOwnTeamWeaponsUsageStatsTotal.size()).append("** different weapons:");

			list = sortedOwnTeamWeaponsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				ownTeamWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamWeaponUsageTotalBuilder.toString());

			StringBuilder enemyTeamWeaponUsageTotalBuilder = new StringBuilder("In total, my enemy teams used a total of **").append(sortedEnemyTeamWeaponsUsageStatsTotal.size()).append("** different weapons:");

			list = sortedEnemyTeamWeaponsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				enemyTeamWeaponUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamWeaponUsageTotalBuilder.toString());
		}
	}

	private void sendSpecialWeaponCountStatsToDiscord(Map<String, Integer> ownUsedSpecials, Map<String, Integer> ownTeamUsedSpecials, Map<String, Integer> enemyTeamUsedSpecials,
													  Map<String, Integer> ownUsedSpecialsTotal, Map<String, Integer> ownTeamUsedSpecialsTotal, Map<String, Integer> enemyTeamUsedSpecialsTotal,
													  Map<String, Integer> ownUsedSpecialsPbs, Map<String, Integer> ownTeamUsedSpecialsPbs, Map<String, Integer> enemyTeamUsedSpecialsPbs,
													  Account account) {
		var maxLimitConfig = configurationRepository.findAllByConfigName("s3DailyStatsMaxLimitSpecials").stream()
			.findFirst()
			.orElseGet(() -> configurationRepository.save(Configuration.builder().configName("s3DailyStatsMaxLimitSpecials").configValue("20").build()));
		var includeSpecialsConfig = configurationRepository.findAllByConfigName("s3DailyStatsIncludeSpecials").stream()
			.findFirst()
			.orElseGet(() -> configurationRepository.save(Configuration.builder().configName("s3DailyStatsIncludeSpecials").configValue("false").build()));

		if ("true".equalsIgnoreCase(includeSpecialsConfig.getConfigValue())) {
			configurationRepository.save(includeSpecialsConfig.toBuilder().configValue("false").build());
			int maxLimit = Integer.parseInt(maxLimitConfig.getConfigValue());

			var sortedOwnSpecialsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
			var sortedOwnTeamSpecialsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
			var sortedEnemyTeamSpecialsUsageStats = new ArrayList<Map.Entry<String, Integer>>();
			var sortedOwnSpecialsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();
			var sortedOwnTeamSpecialsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();
			var sortedEnemyTeamSpecialsUsageStatsTotal = new ArrayList<Map.Entry<String, Integer>>();
			var sortedOwnSpecialsUsageStatsPbs = new ArrayList<Map.Entry<String, Integer>>();
			var sortedOwnTeamSpecialsUsageStatsPbs = new ArrayList<Map.Entry<String, Integer>>();
			var sortedEnemyTeamSpecialsUsageStatsPbs = new ArrayList<Map.Entry<String, Integer>>();

			ownUsedSpecials.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnSpecialsUsageStats::add);

			ownTeamUsedSpecials.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnTeamSpecialsUsageStats::add);

			enemyTeamUsedSpecials.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedEnemyTeamSpecialsUsageStats::add);

			ownUsedSpecialsTotal.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnSpecialsUsageStatsTotal::add);

			ownTeamUsedSpecialsTotal.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnTeamSpecialsUsageStatsTotal::add);

			enemyTeamUsedSpecialsTotal.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedEnemyTeamSpecialsUsageStatsTotal::add);

			ownUsedSpecialsPbs.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnSpecialsUsageStatsPbs::add);

			ownTeamUsedSpecialsPbs.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedOwnTeamSpecialsUsageStatsPbs::add);

			enemyTeamUsedSpecialsPbs.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.forEach(sortedEnemyTeamSpecialsUsageStatsPbs::add);

			// Yesterday
			if (!sortedOwnSpecialsUsageStats.isEmpty()) {
				StringBuilder ownSpecialUsageBuilder = new StringBuilder("Yesterday, I used a total of **").append(sortedOwnSpecialsUsageStats.size()).append("** different special weapons:");

				for (var gearStat : sortedOwnSpecialsUsageStats) {
					// build message
					ownSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownSpecialUsageBuilder.toString());

				StringBuilder ownTeamSpecialUsageBuilder = new StringBuilder("Yesterday, my teams used a total of **").append(sortedOwnTeamSpecialsUsageStats.size()).append("** different special weapons:");

				var list = sortedOwnTeamSpecialsUsageStats.stream().limit(maxLimit).collect(Collectors.toList());
				for (var gearStat : list) {
					// build message
					ownTeamSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamSpecialUsageBuilder.toString());

				StringBuilder enemyTeamSpecialUsageBuilder = new StringBuilder("Yesterday, my enemy teams used a total of **").append(sortedEnemyTeamSpecialsUsageStats.size()).append("** different special weapons:");

				list = sortedEnemyTeamSpecialsUsageStats.stream().limit(maxLimit).collect(Collectors.toList());
				for (var gearStat : list) {
					// build message
					enemyTeamSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamSpecialUsageBuilder.toString());
			}

			// pbs
			if (!sortedOwnSpecialsUsageStatsPbs.isEmpty()) {
				StringBuilder ownPbsSpecialUsageBuilder = new StringBuilder("Yesterday in private battles, I used a total of **").append(sortedOwnSpecialsUsageStatsPbs.size()).append("** different special weapons:");

				for (var gearStat : sortedOwnSpecialsUsageStatsPbs) {
					// build message
					ownPbsSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownPbsSpecialUsageBuilder.toString());

				StringBuilder ownTeamPbsSpecialUsageBuilder = new StringBuilder("Yesterday in private battles, my teams used a total of **").append(sortedOwnTeamSpecialsUsageStatsPbs.size()).append("** different special weapons:");

				for (var gearStat : sortedOwnTeamSpecialsUsageStatsPbs) {
					// build message
					ownTeamPbsSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamPbsSpecialUsageBuilder.toString());

				StringBuilder enemyTeamPbsSpecialUsageBuilder = new StringBuilder("Yesterday in private battles, my enemy teams used a total of **").append(sortedEnemyTeamSpecialsUsageStatsPbs.size()).append("** different special weapons:");

				for (var gearStat : sortedEnemyTeamSpecialsUsageStatsPbs) {
					// build message
					enemyTeamPbsSpecialUsageBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamPbsSpecialUsageBuilder.toString());
			}

			// Total
			StringBuilder ownSpecialUsageTotalBuilder = new StringBuilder("In total, I used a total of **").append(sortedOwnSpecialsUsageStatsTotal.size()).append("** different special weapons:");

			var list = sortedOwnSpecialsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				ownSpecialUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownSpecialUsageTotalBuilder.toString());

			StringBuilder ownTeamSpecialsUsageTotalBuilder = new StringBuilder("In total, my teams used a total of **").append(sortedOwnTeamSpecialsUsageStatsTotal.size()).append("** different special weapons:");

			list = sortedOwnTeamSpecialsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				ownTeamSpecialsUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), ownTeamSpecialsUsageTotalBuilder.toString());

			StringBuilder enemyTeamSpecialsUsageTotalBuilder = new StringBuilder("In total, my enemy teams used a total of **").append(sortedEnemyTeamSpecialsUsageStatsTotal.size()).append("** different special weapons:");

			list = sortedEnemyTeamSpecialsUsageStatsTotal.stream().limit(maxLimit).collect(Collectors.toList());
			for (var gearStat : list) {
				// build message
				enemyTeamSpecialsUsageTotalBuilder.append("\n- ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			}

			discordBot.sendPrivateMessage(account.getDiscordId(), enemyTeamSpecialsUsageTotalBuilder.toString());
		}
	}

	private DailyStatsSaveModel loadYesterdayStats() {
		Configuration yesterdayStatsConfig = configurationRepository.findAllByConfigName(YESTERDAY_CONFIG_NAME).stream().findFirst().orElse(null);

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

			Configuration config = configurationRepository.findAllByConfigName(YESTERDAY_CONFIG_NAME).stream().findFirst().orElse(new Configuration());
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

	private void sendWeaponExpNumbersToDiscord(Map<String, Integer> todayStats, DailyStatsSaveModel yesterdayStats, Account account) {
		var sortedStats = new ArrayList<Map.Entry<String, Integer>>();

		todayStats.entrySet().stream()
			.sorted((a, b) -> orderExpTierNumbersDescending(a.getKey(), b.getKey()))
			.forEach(sortedStats::add);

		StringBuilder expTierBuilder = new StringBuilder("**Current statistics about Exp Tiers of Weapons:**");

		for (var stat : sortedStats) {
			int yesterdayStarCount = yesterdayStats.getPreviousWeaponExpTierCount().getOrDefault(stat.getKey(), 0);

			// build message
			expTierBuilder.append("\n- ").append(stat.getKey()).append(": **").append(stat.getValue()).append("**");
			if (yesterdayStarCount != stat.getValue()) {
				expTierBuilder.append(" (")
					.append(yesterdayStarCount < stat.getValue() ? "+" : "-")
					.append(Math.abs(yesterdayStarCount - stat.getValue()))
					.append(")");
			}

			yesterdayStats.getPreviousWeaponExpTierCount().put(stat.getKey(), stat.getValue());
		}

		var keyList = new ArrayList<>(yesterdayStats.getPreviousWeaponExpTierCount().keySet());
		for (var statKey : keyList) {
			if (!todayStats.containsKey(statKey)) {
				yesterdayStats.getPreviousWeaponExpTierCount().remove(statKey);
			}
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), expTierBuilder.toString());
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

	private int orderExpTierNumbersDescending(String a, String b) {
		var leftValue = 0;
		var rightValue = 0;

		if ("Max Exp".equalsIgnoreCase(a)) {
			leftValue = 1_000_000;
		} else if ("Not purchased yet".equalsIgnoreCase(a)) {
			leftValue = -1_000_000;
		} else {
			leftValue = Integer.parseInt(a.split(" - ")[0].replace("k", "").replace(" ", "").trim());
		}

		if ("Max Exp".equalsIgnoreCase(b)) {
			rightValue = 1_000_000;
		} else if ("Not purchased yet".equalsIgnoreCase(b)) {
			rightValue = -1_000_000;
		} else {
			rightValue = Integer.parseInt(b.split(" - ")[0].replace("k", "").replace(" ", "").trim());
		}

		return Integer.compare(rightValue, leftValue);
	}

	private void sendGearStatsToDiscord(Map<String, Integer> stats, Map<String, Map<Integer, Integer>> gearStarCountPerBrand, DailyStatsSaveModel yesterdayStats, Account account) {
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

		var starsBuilder = new StringBuilder("**Current statistics about Stars on Gear:**");

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
			starsBuilder.append("\n- `").append(isFinishedChar).append("` ").append(gearStat.getKey()).append(": **").append(gearStat.getValue()).append("**");
			if (yesterdayStarCount != gearStat.getValue()) {
				starsBuilder.append(" (")
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

		discordBot.sendPrivateMessage(account.getDiscordId(), starsBuilder.toString());

		sortedStats.stream()
			.filter(b -> !yesterdayStats.getIgnoredBrands().contains(b.getKey()))
			.filter(b -> b.getValue() < 100)
			.map(Map.Entry::getKey)
			.forEach(b -> sendBrandGearStarStatsToDiscord(b, gearStarCountPerBrand, yesterdayStats, account));
	}

	private void sendBrandGearStarStatsToDiscord(String brandName, Map<String, Map<Integer, Integer>> gearStarCountPerBrand, DailyStatsSaveModel yesterdayStats, Account account) {
		var stats = gearStarCountPerBrand.getOrDefault(brandName, new HashMap<>());

		if (stats.isEmpty()) {
			return;
		}

		var sortedStats = stats.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getKey(), a.getKey()))
			.collect(Collectors.toList());

		StringBuilder statMessageBuilder = new StringBuilder("**Current statistics about numbers of Gear with Stars For the `")
			.append(brandName)
			.append("` brand**:");

		var yesterdayStatsForBrand = yesterdayStats.getPreviousStarCountPerBrand().getOrDefault(brandName, new HashMap<>());

		for (var singleStat : sortedStats) {
			statMessageBuilder.append("\n- ").append(singleStat.getKey()).append(" stars: **").append(singleStat.getValue()).append("**");

			int yesterdayStatCount = yesterdayStatsForBrand.getOrDefault(singleStat.getKey(), 0);

			if (yesterdayStatCount != singleStat.getValue()) {
				statMessageBuilder.append(" (")
					.append(yesterdayStatCount < singleStat.getValue() ? "+" : "-")
					.append(Math.abs(yesterdayStatCount - singleStat.getValue()))
					.append(")");
			}

			yesterdayStatsForBrand.put(singleStat.getKey(), singleStat.getValue());
		}

		yesterdayStats.getPreviousStarCountPerBrand().put(brandName, yesterdayStatsForBrand);

		discordBot.sendPrivateMessage(account.getDiscordId(), statMessageBuilder.toString());
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

				wasToday = time.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(1))
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

	private void countStarsOnGear(Map<String, Integer> brandsWithStars, Map<Integer, Integer> starCounts, Map<String, Map<Integer, Integer>> gearStarCountsPerBrand) {
		List<Gear> allOwnedGear = newGearChecker.getAllOwnedGear();
		for (Gear gear : allOwnedGear) {
			var currentBrandStarCount = brandsWithStars.getOrDefault(gear.getBrand().getName(), 0);
			brandsWithStars.put(gear.getBrand().getName(), currentBrandStarCount + gear.getRarity());

			var currentStarCount = starCounts.getOrDefault(gear.getRarity(), 0);
			starCounts.put(gear.getRarity(), currentStarCount + 1);

			var gearStarsForBrand = gearStarCountsPerBrand.getOrDefault(gear.getBrand().getName(), new HashMap<>());
			var currentStarCountForBrand = gearStarsForBrand.getOrDefault(gear.getRarity(), 0);
			gearStarsForBrand.put(gear.getRarity(), currentStarCountForBrand + 1);
			gearStarCountsPerBrand.put(gear.getBrand().getName(), gearStarsForBrand);
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
								 Map<String, Integer> ownUsedWeaponsTotal, Map<String, Integer> ownTeamUsedWeaponsTotal, Map<String, Integer> enemyTeamUsedWeaponsTotal,
								 Map<String, Integer> ownUsedSpecials, Map<String, Integer> ownTeamUsedSpecials, Map<String, Integer> enemyTeamUsedSpecials,
								 Map<String, Integer> ownUsedSpecialsTotal, Map<String, Integer> ownTeamUsedSpecialsTotal, Map<String, Integer> enemyTeamUsedSpecialsTotal) {
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

			var ownSpecials = new HashMap<String, Integer>();
			var ownTeamSpecials = new HashMap<String, Integer>();
			var enemyTeamSpecials = new HashMap<String, Integer>();
			for (var player : result.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream().filter(p -> p.getResult() != null).collect(Collectors.toList())) {
				if (player.getIsMyself()) {
					ownSpecials.put(player.getWeapon().getSpecialWeapon().getName(), player.getResult().getSpecial());
				} else {
					ownTeamSpecials.put(player.getWeapon().getSpecialWeapon().getName(), player.getResult().getSpecial());
				}
			}

			if (ownSpecials.isEmpty()
				&& result.getData().getVsHistoryDetail().getPlayer() != null
				&& result.getData().getVsHistoryDetail().getPlayer().getWeapon() != null
				&& result.getData().getVsHistoryDetail().getPlayer().getResult() != null
				&& result.getData().getVsHistoryDetail().getPlayer().getResult().getSpecial() != null
			) {
				ownSpecials.put(result.getData().getVsHistoryDetail().getPlayer().getWeapon().getSpecialWeapon().getName(),
					result.getData().getVsHistoryDetail().getPlayer().getResult().getSpecial());
			}

			for (var team : result.getData().getVsHistoryDetail().getOtherTeams()) {
				for (var player : team.getPlayers().stream().filter(p -> p.getResult() != null).collect(Collectors.toList())) {
					if (team.getPlayers().size() == 2 && result.getData().getVsHistoryDetail().getMyTeam().getPlayers().size() == 2) {
						// tricolor
						ownTeamSpecials.put(player.getWeapon().getSpecialWeapon().getName(), player.getResult().getSpecial());
					} else {
						enemyTeamSpecials.put(player.getWeapon().getSpecialWeapon().getName(), player.getResult().getSpecial());
					}
				}
			}

			mapUsedWeaponsFromGame(ownUsedWeaponsTotal, ownTeamUsedWeaponsTotal, enemyTeamUsedWeaponsTotal,
				ownWeapon, ownTeamWeapons, enemyTeamWeapons,
				ownSpecials, ownTeamSpecials, enemyTeamSpecials, ownUsedSpecialsTotal, ownTeamUsedSpecialsTotal, enemyTeamUsedSpecialsTotal);

			Instant timeAsInstant = result.getData().getVsHistoryDetail().getPlayedTimeAsInstant();
			if (timeAsInstant != null) {
				LocalDateTime time = LocalDateTime.ofInstant(timeAsInstant, ZoneId.systemDefault());

				var wasToday = time.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(1))
					&& time.isBefore(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));

				if (wasToday) {
					mapUsedWeaponsFromGame(ownUsedWeapons, ownTeamUsedWeapons, enemyTeamUsedWeapons,
						ownWeapon, ownTeamWeapons, enemyTeamWeapons,
						ownSpecials, ownTeamSpecials, enemyTeamSpecials, ownUsedSpecials, ownTeamUsedSpecials, enemyTeamUsedSpecials);
				}
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

				if (player != null && player.getResult() != null && player.getResult().getSpecial() > 0) {
					int currentSpecialWinCount = specialWinResults.getOrDefault(specialWeapon, 0);
					specialWinResults.put(specialWeapon, currentSpecialWinCount + 1);
				}
			}
		} catch (NullPointerException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse result json file '%s' because of an NULLPOINTER OH OH", filename));
			logger.error(e);
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void mapUsedWeaponsFromGame(Map<String, Integer> ownUsedWeapons, Map<String, Integer> ownTeamUsedWeapons, Map<String, Integer> enemyTeamUsedWeapons,
										String ownWeapon, List<String> ownTeamWeapons, List<String> enemyTeamWeapons,
										Map<String, Integer> ownSpecials, Map<String, Integer> ownTeamSpecials, Map<String, Integer> enemyTeamSpecials,
										Map<String, Integer> ownUsedSpecialsTotal, Map<String, Integer> ownTeamUsedSpecialsTotal, Map<String, Integer> enemyTeamUsedSpecialsTotal) {
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

		ownSpecials.forEach((weapon, count) -> {
			int currentOwnUsedSpecialsCount = ownUsedSpecialsTotal.getOrDefault(weapon, 0);
			ownUsedSpecialsTotal.put(weapon, currentOwnUsedSpecialsCount + count);
		});

		ownTeamSpecials.forEach((weapon, count) -> {
			int currentOwnTeamSpecialsCount = ownTeamUsedSpecialsTotal.getOrDefault(weapon, 0);
			ownTeamUsedSpecialsTotal.put(weapon, currentOwnTeamSpecialsCount + count);
		});

		enemyTeamSpecials.forEach((weapon, count) -> {
			int currentEnemyTeamSpecialsCount = enemyTeamUsedSpecialsTotal.getOrDefault(weapon, 0);
			enemyTeamUsedSpecialsTotal.put(weapon, currentEnemyTeamSpecialsCount + count);
		});
	}
}

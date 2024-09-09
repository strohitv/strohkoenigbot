package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3SrResultService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3SrRotationService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3VsResultService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
public class S3Downloader implements ScheduledService {
	private final EntityManager entityManager;

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final TwitchBotClient twitchBotClient;

	private final ConfigurationRepository configurationRepository;
	private final AccountRepository accountRepository;

	private final S3ApiQuerySender requestSender;
	private final ExceptionLogger exceptionLogger;

	private final Splatoon3VsResultService vsResultService;
	private final Splatoon3SrRotationService srRotationService;
	private final Splatoon3SrResultService srResultService;
	private final S3RotationSender rotationSender;
	private final S3StreamStatistics streamStatistics;
	private final S3XPowerDownloader xPowerDownloader;
	private final S3WeaponStatsDownloader weaponStatsDownloader;

	private final S3S3sRunner s3sRunner;

	private final Semaphore semaphore = new Semaphore(1);

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
				.name("S3Downloader_schedule")
				.schedule(CronSchedule.getScheduleString("20 * * * * *"))
				.runnable(this::downloadBattles)
				.errorCleanUpRunnable(this::resetSemaphore)
				.build(),
			ScheduleRequest.builder()
				.name("S3Downloader_s3s_schedule_1")
				.schedule(CronSchedule.getScheduleString("35 5 * * * *"))
				.runnable(this::runS3sIfGamesWereFound)
				.build(),
			ScheduleRequest.builder()
				.name("S3Downloader_s3s_schedule_2")
				.schedule(CronSchedule.getScheduleString("35 35 * * * *"))
				.runnable(this::runS3sIfGamesWereFound)
				.build(),
			ScheduleRequest.builder()
				.name("S3Downloader_s3s_fix_broken_database_entries")
				.schedule(CronSchedule.getScheduleString("35 5 * * * *"))
				.runnable(this::fixBrokenDatabaseEntries)
				.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	public void fixBrokenDatabaseEntries() {
		vsResultService.fixDoubledEntries();
	}

	private final List<Integer> refreshMinutes = List.of(12, 35);

	@Getter
	@Setter
	private boolean pauseDownloader = false;

	private Instant wentLiveInstant = null;
	private boolean shouldRunS3s = false;

	public void goLive(Instant wentLiveTime) {
		wentLiveInstant = wentLiveTime;
		streamStatistics.reset();
		xPowerDownloader.fillXPower();
		weaponStatsDownloader.fillWeaponStats();

		logSender.sendLogs(logger, "went live on twitch!");
	}

	public void goOffline() {
		wentLiveInstant = null;
		streamStatistics.reset();

		logSender.sendLogs(logger, "went offline on twitch!");
	}

	public void resetSemaphore() {
		if (!semaphore.tryAcquire()) {
			semaphore.release();
		}
	}

	public void runS3sIfGamesWereFound() {
		if (shouldRunS3s) {
			logSender.sendLogs(logger, "Found games which need to be imported to stat.ink / running s3s");

			// start refresh of s3s script asynchronously
			s3sRunner.runS3S();

			shouldRunS3s = false;
		}
	}

	public void downloadBattles() {
		downloadBattles(false);
	}

	public void downloadBattles(boolean force) {
		if (!semaphore.tryAcquire()) {
			logSender.sendLogs(logger, "Skipping import because there's already one import running");
			return;
		}

		logger.debug("Enter download battles for Splatoon 3 games...");

		if (pauseDownloader && !force) {
			logger.info("Downloader is paused, stopping loading Splatoon 3 games early");
			return;
		}

		if (wentLiveInstant == null && twitchBotClient.getWentLiveTime() != null) {
			goLive(twitchBotClient.getWentLiveTime());
		} else if (wentLiveInstant != null && twitchBotClient.getWentLiveTime() == null) {
			goOffline();
		}

		if (wentLiveInstant != null
			|| force
			|| refreshMinutes.contains(LocalDateTime.now().getMinute())) {
			logger.info("Loading Splatoon 3 games...");
			try {
				if (wentLiveInstant != null) {
					xPowerDownloader.fillXPower();
					weaponStatsDownloader.fillWeaponStats();
				}

				downloadGamesDecideWay();
			} catch (Exception e) {
				try {
					logSender.sendLogs(logger, "An exception occurred during S3 download\nSee logs for details!");
					exceptionLogger.logException(logger, e);
				} catch (Exception ignored) {
				}

				logger.error(e);
			}

			logger.info("Finished loading Splatoon 3 games.");
		}

		semaphore.release();
	}

	private void downloadGamesDecideWay() {
		var useNewWay = configurationRepository.findAllByConfigName("s3UseDatabase").stream()
			.map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
			.findFirst()
			.orElse(false);

		if (useNewWay) {
			var oldFLushMode = entityManager.getFlushMode();
			entityManager.setFlushMode(FlushModeType.COMMIT);

			importAllJsonFilesIntoDatabase();
			importResultsFromS3Api();

			entityManager.setFlushMode(oldFLushMode);
		} else {
			doDownloadBattles();
		}
	}

	private void importResultsFromS3Api() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			var importedVsGames = importVsResultsOfAccountFromS3Api(account);
			var importedSrGames = importSrResultsOfAccountFromS3Api(account);

			if (wentLiveInstant != null) {
				var allNewGamesDuringStream = importedVsGames.keySet().stream()
					.flatMap(mode -> importedVsGames.get(mode).stream())
					.collect(Collectors.toList());

				streamStatistics.addGames(allNewGamesDuringStream);

				if (!allNewGamesDuringStream.isEmpty()) {
					streamStatistics.exportHtml();
				}
			}

			StringBuilder builder = new StringBuilder("Found new Splatoon 3 results:");
			importedVsGames.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().getName()))
				.forEach(entry -> {
					if (!entry.getValue().isEmpty()) {
						builder.append(String.format("\n- **%d** new %s battle results", entry.getValue().size(), entry.getKey().getName()));
					}
				});

			importedSrGames.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().getName()))
				.forEach(entry -> {
					if (!entry.getValue().isEmpty()) {
						builder.append(String.format("\n- **%d** new %s shift results", entry.getValue().size(), entry.getKey().getName()));
					}
				});

			var foundGames = importedVsGames.entrySet().stream().anyMatch((res) -> !res.getValue().isEmpty())
				|| importedSrGames.entrySet().stream().anyMatch((res) -> !res.getValue().isEmpty());

			shouldRunS3s |= foundGames;

			if (foundGames) {
				logSender.sendLogs(logger, builder.toString());
			}
		}
	}

	private Map<Splatoon3VsMode, List<Splatoon3VsResult>> importVsResultsOfAccountFromS3Api(Account account) {
		return S3RequestKey.getOnlineBattles().stream()
			.flatMap(requestKey -> {
				String gameListResponse = requestSender.queryS3Api(account, requestKey);
				return streamResults(parseBattleResultsSneakyThrow(gameListResponse));
			})
			.flatMap(hgn -> Arrays.stream(hgn.getHistoryDetails().getNodes()))
			.filter(vsResultService::notFound)
			.map(hgn -> {
				String matchJson = requestSender.queryS3Api(account, S3RequestKey.GameDetail, "vsResultId", hgn.getId());
				var parsed = parseSingleResultSneakyThrow(matchJson);
				parsed.setJsonSave(matchJson);
				return parsed;
			})
			.sorted(Comparator.comparing(parsed -> parsed.getData().getVsHistoryDetail().getPlayedTimeAsInstant()))
			.map(parsed -> vsResultService.ensureResultExists(parsed.getData().getVsHistoryDetail(), parsed.getJsonSave()))
			.collect(groupingBy(Splatoon3VsResult::getMode));
	}

	private Map<Splatoon3SrMode, List<Splatoon3SrResult>> importSrResultsOfAccountFromS3Api(Account account) {
		String gameListResponse = requestSender.queryS3Api(account, S3RequestKey.Salmon);

		return streamResults(parseBattleResultsSneakyThrow(gameListResponse))
			.peek(srRotationService::ensureDummyRotationExists)
			.flatMap(hgn -> Arrays.stream(hgn.getHistoryDetails().getNodes()))
			.filter(srResultService::notFound)
			.map(hgn -> {
				String matchJson = requestSender.queryS3Api(account, S3RequestKey.SalmonDetail, "coopHistoryDetailId", hgn.getId());
				var parsed = parseSingleResultSneakyThrow(matchJson);
				parsed.setJsonSave(matchJson);
				return parsed;
			})
			.sorted(Comparator.comparing(parsed -> parsed.getData().getCoopHistoryDetail().getPlayedTimeAsInstant()))
			.map(parsed -> srResultService.ensureResultExists(parsed.getData().getCoopHistoryDetail(), parsed.getJsonSave()))
			.collect(groupingBy(res -> res.getRotation().getMode()));
	}

	private BattleResults parseBattleResultsSneakyThrow(String gameList) {
		try {
			return objectMapper.readValue(gameList, BattleResults.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private BattleResult parseSingleResultSneakyThrow(String gameJson) {
		try {
			return objectMapper.readValue(gameJson, BattleResult.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private Stream<BattleResults.HistoryGroupsNodes> streamResults(BattleResults results) {
		var unboxed = results.getData();
		if (unboxed.getLatestBattleHistories() != null) {
			return Arrays.stream(unboxed.getLatestBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getRegularBattleHistories() != null) {
			return Arrays.stream(unboxed.getRegularBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getBankaraBattleHistories() != null) {
			return Arrays.stream(unboxed.getBankaraBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getXBattleHistories() != null) {
			return Arrays.stream(unboxed.getXBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getEventBattleHistories() != null) {
			return Arrays.stream(unboxed.getEventBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getPrivateBattleHistories() != null) {
			return Arrays.stream(unboxed.getPrivateBattleHistories().getHistoryGroups().getNodes());
		} else if (unboxed.getCoopResult() != null) {
			return Arrays.stream(unboxed.getCoopResult().getHistoryGroups().getNodes());
		}

		return Stream.of();
	}

	private void importAllJsonFilesIntoDatabase() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			String accountUUIDHash = String.format("%05d", account.getId());
			importJsonResultsIntoDatabase(accountUUIDHash, true);
		}
	}

	// todo switch download from storing into json files to storing into databas

	private void doDownloadBattles() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			String accountUUIDHash = String.format("%05d", account.getId()); // UUID.nameUUIDFromBytes(Path.of(configFileLocation, "config.txt").toString().getBytes()).toString();
			Path directory = Path.of("game-results", accountUUIDHash);
			if (!Files.exists(directory)) {
				try {
					Files.createDirectories(directory);
				} catch (IOException e) {
					logSender.sendLogs(logger, String.format("Could not create game directory!! %s", directory));
					continue;
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
					logSender.sendLogs(logger, "COULD NOT OPEN FILE!!!");
					continue;
				}
			} catch (IOException e) {
				logSender.sendLogs(logger, "IOEXCEPTION WHILE OPENING OR WRITING OVERVIEW FILE!!!");
				logger.error(e);
				continue;
			}

//			String homeResponse = requestSender.queryS3Api(account, S3RequestKey.Home.getKey(), "naCountry", "US");
//			logger.debug(homeResponse);
//
//			if (!homeResponse.contains("currentPlayer")) {
//				logSender.sendLogs(logger, "Could not load homepage from SplatNet3");
//				continue;
//			}

			preventNullFields(allDownloadedGames);

			ZonedDateTime now = Instant.now().atZone(ZoneId.systemDefault());
			String timeString = String.format("%04d-%02d-%02d_%02d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond());

			List<String> onlineRegularGamesToDownload = new ArrayList<>();
			List<String> onlineAnarchyGamesToDownload = new ArrayList<>();
			List<String> onlineXRankGamesToDownload = new ArrayList<>();
			List<String> onlineChallengeGamesToDownload = new ArrayList<>();
			List<String> onlinePrivateGamesToDownload = new ArrayList<>();

			for (S3RequestKey key : S3RequestKey.getOnlineBattles()) {
				downloadPvPGames(account, directory, allDownloadedGames, timeString, onlineRegularGamesToDownload, onlineAnarchyGamesToDownload, onlineXRankGamesToDownload, onlineChallengeGamesToDownload, onlinePrivateGamesToDownload, key);
			}

			for (String matchId : onlineRegularGamesToDownload) {
				storeOnlineGame(account, "Regular", directory, allDownloadedGames.getRegular_games(), matchId);
			}

			for (String matchId : onlineAnarchyGamesToDownload) {
				storeOnlineGame(account, "Anarchy", directory, allDownloadedGames.getAnarchy_games(), matchId);
			}

			for (String matchId : onlineXRankGamesToDownload) {
				storeOnlineGame(account, "XRank", directory, allDownloadedGames.getX_rank_games(), matchId);
			}

			for (String matchId : onlineChallengeGamesToDownload) {
				storeOnlineGame(account, "Challenge", directory, allDownloadedGames.getChallenge_games(), matchId);
			}

			for (String matchId : onlinePrivateGamesToDownload) {
				storeOnlineGame(account, "Private", directory, allDownloadedGames.getPrivate_games(), matchId);
			}

			String salmonListResponse = requestSender.queryS3Api(account, S3RequestKey.Salmon);
			logger.debug(salmonListResponse);

			List<String> salmonShiftsToDownload = new ArrayList<>();
			if (salmonListResponse.contains("coop")) {
				downloadSalmonRunGames(account, directory, allDownloadedGames, timeString, salmonListResponse, salmonShiftsToDownload);
			} else {
				logSender.sendLogs(logger, "Could not load Salmon Run Stats from SplatNet3");
			}

			try {
				objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
			} catch (IOException e) {
				logSender.sendLogs(logger, "IOEXCEPTION WHILE WRITING REFRESHED OVERVIEW FILE!!!");
				logger.error(e);
			}

			if (!onlineRegularGamesToDownload.isEmpty()
				|| !onlineAnarchyGamesToDownload.isEmpty()
				|| !onlineXRankGamesToDownload.isEmpty()
				|| !onlineChallengeGamesToDownload.isEmpty()
				|| !onlinePrivateGamesToDownload.isEmpty()
				|| !salmonShiftsToDownload.isEmpty()) {
				String message = "Found new Splatoon 3 results:";

				if (!onlineRegularGamesToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new regular battles", message, onlineRegularGamesToDownload.size());
				}

				if (!onlineAnarchyGamesToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new anarchy battles", message, onlineAnarchyGamesToDownload.size());
				}

				if (!onlineXRankGamesToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new x rank battles", message, onlineXRankGamesToDownload.size());
				}

				if (!onlineChallengeGamesToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new challenge battles", message, onlineChallengeGamesToDownload.size());
				}

				if (!onlinePrivateGamesToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new private battles", message, onlinePrivateGamesToDownload.size());
				}

				if (!salmonShiftsToDownload.isEmpty()) {
					message = String.format("%s\n- **%d** new salmon run shifts", message, salmonShiftsToDownload.size());
				}

				logSender.sendLogs(logger, message);

				// start refresh of s3s script asynchronously
				s3sRunner.runS3S();
			}

			if (LocalDateTime.now(ZoneId.systemDefault()).getHour() == 8) { // = 9:35 am
				tryParseAllBattles(accountUUIDHash);
			}
		}
	}

	private void preventNullFields(ConfigFile.DownloadedGameList allDownloadedGames) {
		if (allDownloadedGames.getRegular_games() == null) {
			allDownloadedGames.setRegular_games(new HashMap<>());
		}

		if (allDownloadedGames.getAnarchy_games() == null) {
			allDownloadedGames.setAnarchy_games(new HashMap<>());
		}

		if (allDownloadedGames.getX_rank_games() == null) {
			allDownloadedGames.setX_rank_games(new HashMap<>());
		}

		if (allDownloadedGames.getChallenge_games() == null) {
			allDownloadedGames.setChallenge_games(new HashMap<>());
		}

		if (allDownloadedGames.getPrivate_games() == null) {
			allDownloadedGames.setPrivate_games(new HashMap<>());
		}

		if (allDownloadedGames.getSalmon_games() == null) {
			allDownloadedGames.setSalmon_games(new HashMap<>());
		}
	}

	public void tryParseAllBattles(String folderName) {
		logSender.sendLogs(logger, String.format("Loading Splatoon 3 games for account with folder name '%s'...", folderName));

		Path directory = Path.of("game-results", folderName);
		if (directoryCreationFails(directory)) {
			logSender.sendLogs(logger, String.format("Folder name '%s' does not exist an could not be created!", folderName));
			return;
		}

		ConfigFile.DownloadedGameList allDownloadedGames = getAllDownloadedGames(directory);

		if (allDownloadedGames == null) return;

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getRegular_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getX_rank_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getChallenge_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getPrivate_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		logSender.sendLogs(logger, String.format("Done with loading Splatoon 3 games for account with folder name '%s'...", folderName));
	}

	@Getter
	private static class BattleCounter {
		private int count = 0;

		public void increaseCount() {
			count++;
		}
	}

	@SneakyThrows
	public void importJsonResultsIntoDatabase(String folderName, boolean shouldDelete) {
		var oldFLushMode = entityManager.getFlushMode();
		entityManager.setFlushMode(FlushModeType.COMMIT);

		Path directory = Path.of("game-results", folderName);
		if (directoryCreationFails(directory)) {
			logSender.sendLogs(logger, String.format("Folder name '%s' does not exist an could not be created!", folderName));
			return;
		}

		rotationSender.importSrRotationsFromGameResultsFolder(folderName, shouldDelete);

		ConfigFile.DownloadedGameList allDownloadedGames = getAllDownloadedGames(directory);
		if (allDownloadedGames == null) return;

		var battleCounter = new BattleCounter();

		Stream.of(
				allDownloadedGames.getRegular_games().entrySet(),
				allDownloadedGames.getAnarchy_games().entrySet(),
				allDownloadedGames.getX_rank_games().entrySet(),
				allDownloadedGames.getChallenge_games().entrySet(),
				allDownloadedGames.getPrivate_games().entrySet(),
				allDownloadedGames.getSalmon_games().entrySet()
			)
			.flatMap(Collection::stream)
			.map(Map.Entry::getValue)
			.filter(sg -> directory.resolve(sg.getFilename()).toAbsolutePath().toFile().exists())
			.sorted((a, b) -> getPlayedTime(a, directory).compareTo(getPlayedTime(b, directory)))
			.forEach(sg -> {
				if (battleCounter.getCount() == 0) {
					logSender.sendLogs(logger, String.format("Importing Splatoon 3 games of account with folder name '%s' from json into database...", folderName));
				}

				var fileContent = readFile(sg, directory);
				var result = parseBattleResult(fileContent);

				try {
					if (result.getData().getVsHistoryDetail() != null) {
						vsResultService.ensureResultExists(result.getData().getVsHistoryDetail(), fileContent);
					} else {
						srResultService.ensureResultExists(result.getData().getCoopHistoryDetail(), fileContent);
					}

					if (shouldDelete) {
						Files.deleteIfExists(directory.resolve(sg.getFilename()).toAbsolutePath());
					}
				} catch (Exception ex) {
					logSender.sendLogs(logger, String.format("Folder name '%s': Exception during import of file '%s', see logs for details!", folderName, sg.getFilename()));
					logger.error(ex);
				}

				battleCounter.increaseCount();

				if (battleCounter.getCount() % 250 == 0) {
					logSender.sendLogs(logger, String.format("Folder name '%s': Total imported games now at %d", folderName, battleCounter.getCount()));
				}
			});

		if (battleCounter.getCount() > 0) {
			logSender.sendLogs(logger, String.format("Done with importing Splatoon 3 games of account with folder name '%s' from json into database. Total number of imported games: %d", folderName, battleCounter.getCount()));
		}

		entityManager.setFlushMode(oldFLushMode);
	}

	private Instant getPlayedTime(ConfigFile.StoredGame sg, Path directory) {
		var fileContent = readFile(sg, directory);
		var parsedResult = parseBattleResult(fileContent);

		return parsedResult.getData().getVsHistoryDetail() != null
			? parsedResult.getData().getVsHistoryDetail().getPlayedTimeAsInstant()
			: parsedResult.getData().getCoopHistoryDetail().getPlayedTimeAsInstant();
	}

	private boolean directoryCreationFails(Path directory) {
		if (!Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				logSender.sendLogs(logger, String.format("Could not create game directory!! %s", directory));
				return true;
			}
		}
		return false;
	}

	@Nullable
	private ConfigFile.DownloadedGameList getAllDownloadedGames(Path directory) {
		File battleOverviewFile = directory.resolve("Already_Downloaded_Battles.json").toFile();
		ConfigFile.DownloadedGameList allDownloadedGames;
		try {
			if (battleOverviewFile.exists() && Files.size(battleOverviewFile.toPath()) > 0) { // if file already exists will do nothing
				allDownloadedGames = objectMapper.readValue(battleOverviewFile, ConfigFile.DownloadedGameList.class);
			} else if (battleOverviewFile.exists() || battleOverviewFile.createNewFile()) {
				allDownloadedGames = new ConfigFile.DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
				objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
			} else {
				logSender.sendLogs(logger, "COULD NOT OPEN FILE!!!");
				return null;
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, "IOEXCEPTION WHILE OPENING OR WRITING OVERVIEW FILE!!!");
			logger.error(e);
			return null;
		}

		preventNullFields(allDownloadedGames);
		return allDownloadedGames;
	}

	private void parseBattleResult(Map.Entry<String, ConfigFile.StoredGame> game, Path directory) {
		parseBattleResult(game.getValue(), directory);
	}

	private String readFile(ConfigFile.StoredGame game, Path directory) {
		String filename = directory.resolve(game.getFilename()).toAbsolutePath().toString();

		String result = null;
		try (var stream = new FileInputStream(filename)) {
			logger.info(filename);
			result = new String(stream.readAllBytes());
			logger.debug(result);
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't read file '%s' OH OH", filename));
			logger.error(e);
		}

		return result;
	}

	private BattleResult parseBattleResult(String json) {
		BattleResult result = null;
		try {
			result = objectMapper.readValue(json, BattleResult.class);
			logger.debug(result);
		} catch (IOException e) {
			logSender.sendLogs(logger, "Couldn't parse battle result json content OH OH");
			logger.error(e);
		}

		return result;
	}

	private void parseBattleResult(ConfigFile.StoredGame game, Path directory) {
		String filename = directory.resolve(game.getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			var result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse battle result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void downloadPvPGames(Account account, Path directory, ConfigFile.DownloadedGameList allDownloadedGames, String timeString, List<String> onlineRegularGamesToDownload, List<String> onlineAnarchyGamesToDownload, List<String> onlineXRankGamesToDownload, List<String> onlineChallengeGamesToDownload, List<String> onlinePrivateGamesToDownload, S3RequestKey key) {
		String gameListResponse = requestSender.queryS3Api(account, key);
		logger.debug(gameListResponse);
		if (!gameListResponse.contains("assistAverage")) {
			logSender.sendLogs(logger, String.format("Could not load results from SplatNet3: %s", key));
			logger.error(gameListResponse);
			return;
		}

		BattleResults parsedResult;
		try {
			parsedResult = objectMapper.readValue(gameListResponse, BattleResults.class);
		} catch (JsonProcessingException e) {
			logSender.sendLogs(logger, String.format("Could not parse results from SplatNet3: %s", key));
			logger.error(e);
			return;
		}

		logger.debug(parsedResult);

		// Eventuell auch die latest results pullen?
		// Aktuell nicht umgesetzt, da selbe matches unterschiedliche IDs haben in den unterschiedlichen Listen
		if (parsedResult.getData().getRegularBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getRegular_games(), onlineRegularGamesToDownload, parsedResult.getData().getRegularBattleHistories());

			if (!onlineRegularGamesToDownload.isEmpty()) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineRegularGamesToDownload);
		}

		if (parsedResult.getData().getBankaraBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getAnarchy_games(), onlineAnarchyGamesToDownload, parsedResult.getData().getBankaraBattleHistories());

			if (!onlineAnarchyGamesToDownload.isEmpty()) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineAnarchyGamesToDownload);
		}

		if (parsedResult.getData().getXBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getX_rank_games(), onlineXRankGamesToDownload, parsedResult.getData().getXBattleHistories());

			if (!onlineXRankGamesToDownload.isEmpty()) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineXRankGamesToDownload);
		}

		if (parsedResult.getData().getEventBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getChallenge_games(), onlineChallengeGamesToDownload, parsedResult.getData().getEventBattleHistories());

			if (!onlineChallengeGamesToDownload.isEmpty()) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineChallengeGamesToDownload);
		}

		if (parsedResult.getData().getPrivateBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getPrivate_games(), onlinePrivateGamesToDownload, parsedResult.getData().getPrivateBattleHistories());

			if (!onlinePrivateGamesToDownload.isEmpty()) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlinePrivateGamesToDownload);
		}
	}

	private void downloadSalmonRunGames(Account account, Path directory, ConfigFile.DownloadedGameList allDownloadedGames, String timeString, String salmonListResponse, List<String> salmonShiftsToDownload) {
		BattleResults parsedResult = null;
		try {
			parsedResult = objectMapper.readValue(salmonListResponse, BattleResults.class);
		} catch (JsonProcessingException e) {
			logSender.sendLogs(logger, "Could not parse results from SplatNet3: Salmon Run");
			logger.error(e);
		}

		if (parsedResult != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getSalmon_games(), salmonShiftsToDownload, parsedResult.getData().getCoopResult());

			if (!salmonShiftsToDownload.isEmpty()) {
				String filename = String.format("Salmon_List_%s.json", timeString);
				saveFile(directory.resolve(filename), salmonListResponse);
			}

			for (String salmonShiftId : salmonShiftsToDownload) {
				String salmonShiftJson = requestSender.queryS3Api(account, S3RequestKey.SalmonDetail, "coopHistoryDetailId", salmonShiftId);
				logger.debug(salmonShiftJson);

				if (!salmonShiftJson.contains("coopHistoryDetail")) {
					logSender.sendLogs(logger, "could not load match detail from splatnet!");
					continue;
				}

				String filename = String.format("Salmon_Result_%05d.json", allDownloadedGames.getSalmon_games().size() + 1);
				if (saveFile(directory.resolve(filename), salmonShiftJson)) {
					try {
						BattleResults.SingleMatchResult data = objectMapper.readValue(salmonShiftJson, BattleResults.SingleMatchResult.class);

						allDownloadedGames.getSalmon_games().put(salmonShiftId, new ConfigFile.StoredGame(allDownloadedGames.getSalmon_games().size() + 1, filename, Instant.parse(data.getData().getCoopHistoryDetail().getPlayedTime())));
					} catch (JsonProcessingException e) {
						logSender.sendLogs(logger, "Could not parse single salmon shift result!");
						logger.error(e);
					}
				}
			}
		}
	}

	private void storeOnlineGame(Account account, String filenamePrefix, Path directory, Map<String, ConfigFile.StoredGame> games, String matchId) {
		String matchJson = requestSender.queryS3Api(account, S3RequestKey.GameDetail, "vsResultId", matchId);
		logger.debug(matchJson);

		if (!matchJson.contains("vsHistoryDetail")) {
			logSender.sendLogs(logger, "could not load match detail from splatnet!");
		}

		String filename = String.format("%s_Result_%05d.json", filenamePrefix, games.size() + 1);
		if (saveFile(directory.resolve(filename), matchJson)) {
			try {
				BattleResults.SingleMatchResult data = objectMapper.readValue(matchJson, BattleResults.SingleMatchResult.class);

				games.put(matchId, new ConfigFile.StoredGame(games.size() + 1, filename, Instant.parse(data.getData().getVsHistoryDetail().getPlayedTime())));
			} catch (JsonProcessingException e) {
				logSender.sendLogs(logger, "Could not parse single match result!");
				logger.error(e);
			}
		}
	}

	private boolean saveFile(Path path, String content) {
		Path directory = path.getParent();
		if (!Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				logSender.sendLogs(logger, String.format("Could not create directory for file! %s", path));
				return false;
			}
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toAbsolutePath().toFile()))) {
			writer.write(content);
			return true;
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Could not write file! %s", path));
		}

		return false;
	}

	private void storeIdsOfMatchesToDownload(Map<String, ConfigFile.StoredGame> allDownloadedGames, List<String> idsOfMatchesToDownload, BattleResults.BattleHistories battleHistories) {
		for (BattleResults.HistoryGroupsNodes historyGroupNode : battleHistories.getHistoryGroups().getNodes()) {
			for (BattleResults.HistoryGroupMatch singleMatch : historyGroupNode.getHistoryDetails().getNodes()) {
				if (!allDownloadedGames.containsKey(singleMatch.getId()) && !idsOfMatchesToDownload.contains(singleMatch.getId())) {
					idsOfMatchesToDownload.add(0, singleMatch.getId());
				}
			}
		}
	}
}

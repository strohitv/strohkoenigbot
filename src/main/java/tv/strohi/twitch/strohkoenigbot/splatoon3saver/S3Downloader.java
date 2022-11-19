package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3GTokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ConfigFileConnector;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3Downloader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private Instant lastSuccessfulAttempt = Instant.now().minus(1, ChronoUnit.HOURS);

	private final ConfigurationRepository configurationRepository;
	private final AccountRepository accountRepository;
	private final S3ApiQuerySender requestSender;
	private final S3GTokenRefresher gTokenRefresher;
	private final ConfigFileConnector configFileConnector;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3Downloader_schedule", CronSchedule.getScheduleString("30 35 * * * *"), this::downloadStuffExceptionSafe);
	}

	//	@Scheduled(cron = "30 35 * * * *")
	//	@Scheduled(cron = "30 * * * * *")
	public void downloadStuffExceptionSafe() {
		logSender.sendLogs(logger, "Loading Splatoon 3 games...");
		try {
			downloadStuff();
		} catch (Exception e) {
			try {
				logSender.sendLogs(logger, String.format("An exception occurred during S3 download: '%s'\nSee logs for details!", e.getMessage()));
			} catch (Exception ignored) {
			}

			logger.error(e);
		}
	}

	private void downloadStuff() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			lastSuccessfulAttempt = Instant.now();

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
					allDownloadedGames = new ConfigFile.DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
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

			String homeResponse = requestSender.queryS3Api(account, S3RequestKey.Home.getKey());
			logger.debug(homeResponse);

			if (!homeResponse.contains("currentPlayer")) {
				logSender.sendLogs(logger, "Could not load homepage from SplatNet3");
				continue;
			}

			ZonedDateTime now = Instant.now().atZone(ZoneId.systemDefault());
			String timeString = String.format("%04d-%02d-%02d_%02d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond());

			List<String> onlineRegularGamesToDownload = new ArrayList<>();
			List<String> onlineAnarchyGamesToDownload = new ArrayList<>();
			List<String> onlinePrivateGamesToDownload = new ArrayList<>();
			for (S3RequestKey key : S3RequestKey.getOnlineBattles()) {
				downloadPvPGames(account, directory, allDownloadedGames, timeString, onlineRegularGamesToDownload, onlineAnarchyGamesToDownload, onlinePrivateGamesToDownload, key);
			}

			for (String matchId : onlineRegularGamesToDownload) {
				storeOnlineGame(account, "Regular", directory, allDownloadedGames.getRegular_games(), matchId);
			}

			for (String matchId : onlineAnarchyGamesToDownload) {
				storeOnlineGame(account, "Anarchy", directory, allDownloadedGames.getAnarchy_games(), matchId);
			}

			for (String matchId : onlinePrivateGamesToDownload) {
				storeOnlineGame(account, "Private", directory, allDownloadedGames.getPrivate_games(), matchId);
			}

			String salmonListResponse = requestSender.queryS3Api(account, S3RequestKey.Salmon.getKey());
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

			if (onlineRegularGamesToDownload.size() > 0
					|| onlineAnarchyGamesToDownload.size() > 0
					|| onlinePrivateGamesToDownload.size() > 0
					|| salmonShiftsToDownload.size() > 0) {
				String message = "Found new Splatoon 3 results:";

				if (onlineRegularGamesToDownload.size() > 0) {
					message = String.format("%s\n- **%d** new regular battles", message, onlineRegularGamesToDownload.size());
				}

				if (onlineAnarchyGamesToDownload.size() > 0) {
					message = String.format("%s\n- **%d** new anarchy battles", message, onlineAnarchyGamesToDownload.size());
				}

				if (onlinePrivateGamesToDownload.size() > 0) {
					message = String.format("%s\n- **%d** new private battles", message, onlinePrivateGamesToDownload.size());
				}

				if (salmonShiftsToDownload.size() > 0) {
					message = String.format("%s\n- **%d** new salmon run shifts", message, salmonShiftsToDownload.size());
				}

				logSender.sendLogs(logger, message);

				// start refresh of s3s script asynchronously
				runS3S();
			}


			if (LocalDateTime.now(ZoneId.systemDefault()).getHour() == 8) { // = 9:35 am
				tryParseAllBattles(accountUUIDHash);
			}
		}
	}

	private void runS3S() {
		new Thread(() -> {
			logSender.sendLogs(logger, "Starting s3s refresh...");

			String scriptFormatString = configurationRepository.findByConfigName("s3sScript").stream().map(Configuration::getConfigValue).findFirst().orElse("python3 %s/s3s.py -o");

			List<Configuration> s3sLocations = configurationRepository.findByConfigName("s3sLocation");
			if (s3sLocations.size() == 0) return;

			Runtime rt = Runtime.getRuntime();
			for (Configuration singleS3SLocation : s3sLocations) {
				String configFileLocation = singleS3SLocation.getConfigValue();
				String completeCommand = String.format(scriptFormatString, configFileLocation).trim();

				logSender.sendLogs(logger, String.format("Starting download for location %s", configFileLocation));

				if (!gTokenRefresher.refreshGToken(rt, configFileLocation, completeCommand)) {
					logger.warn("Did not work..");
					if (lastSuccessfulAttempt.isBefore(Instant.now().minus(3, ChronoUnit.HOURS))) {
						logSender.sendLogs(logger, "Exception while executing s3s process!! Result wasn't 0 for at least three hours now!");
					}

					continue;
				}

				ConfigFile configFile = configFileConnector.readConfigFile(configFileLocation);

				if (configFile == null) continue;

				String accountUUIDHash = UUID.nameUUIDFromBytes(Path.of(configFileLocation, "config.txt").toString().getBytes()).toString();
				Path directory = Path.of("game-results", accountUUIDHash);
				if (!Files.exists(directory)) {
					try {
						Files.createDirectories(directory);
					} catch (IOException e) {
						logSender.sendLogs(logger, String.format("Could not create game directory!! %s", directory));
						continue;
					}
				}

				File file = new File(".");
				List<String> directories = Arrays.stream(Objects.requireNonNull(file.list((current, name) -> new File(current, name).isDirectory()))).filter(name -> name.startsWith("export-")).collect(Collectors.toList());

				// move exported folders to back up directory
				for (String dir : directories) {
					try {
						logSender.sendLogs(logger, String.format("Moving directory %s", dir));
						Files.move(new File(dir).toPath(), directory.resolve(dir), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						logSender.sendLogs(logger, String.format("could not move directory %s", dir));
						logger.error(e);
					}
				}
			}

			logSender.sendLogs(logger, "Finished s3s refresh");
		}).start();
	}

	public void tryParseAllBattles(String accountUUIDHash) {
		logSender.sendLogs(logger, String.format("Loading Splatoon 3 games for account with hash '%s'...", accountUUIDHash));

		Path directory = Path.of("game-results", accountUUIDHash);
		if (!Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				logSender.sendLogs(logger, String.format("Could not create game directory!! %s", directory));
				return;
			}
		}

		File battleOverviewFile = directory.resolve("Already_Downloaded_Battles.json").toFile();
		ConfigFile.DownloadedGameList allDownloadedGames;
		try {
			if (battleOverviewFile.exists() && Files.size(battleOverviewFile.toPath()) > 0) { // if file already exists will do nothing
				allDownloadedGames = objectMapper.readValue(battleOverviewFile, ConfigFile.DownloadedGameList.class);
			} else if (battleOverviewFile.exists() || battleOverviewFile.createNewFile()) {
				allDownloadedGames = new ConfigFile.DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
				objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
			} else {
				logSender.sendLogs(logger, "COULD NOT OPEN FILE!!!");
				return;
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, "IOEXCEPTION WHILE OPENING OR WRITING OVERVIEW FILE!!!");
			logger.error(e);
			return;
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getRegular_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getPrivate_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
			parseBattleResult(game, directory);
		}

		logSender.sendLogs(logger, String.format("Done with loading Splatoon 3 games for account with hash '%s'...", accountUUIDHash));
	}

	private void parseBattleResult(Map.Entry<String, ConfigFile.StoredGame> game, Path directory) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse battle result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void downloadPvPGames(Account account, Path directory, ConfigFile.DownloadedGameList allDownloadedGames, String timeString, List<String> onlineRegularGamesToDownload, List<String> onlineAnarchyGamesToDownload, List<String> onlinePrivateGamesToDownload, S3RequestKey key) {
		String gameListResponse = requestSender.queryS3Api(account, key.getKey());
		logger.debug(gameListResponse);
		if (!gameListResponse.contains("assistAverage")) {
			logSender.sendLogs(logger, String.format("Could not load results from SplatNet3: %s", key));
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

			if (onlineRegularGamesToDownload.size() > 0) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineRegularGamesToDownload);
		}

		if (parsedResult.getData().getBankaraBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getAnarchy_games(), onlineAnarchyGamesToDownload, parsedResult.getData().getBankaraBattleHistories());

			if (onlineAnarchyGamesToDownload.size() > 0) {
				String filename = String.format("%s_List_%s.json", key, timeString);
				saveFile(directory.resolve(filename), gameListResponse);
			}
			logger.debug(onlineAnarchyGamesToDownload);
		}

		if (parsedResult.getData().getPrivateBattleHistories() != null) {
			storeIdsOfMatchesToDownload(allDownloadedGames.getPrivate_games(), onlinePrivateGamesToDownload, parsedResult.getData().getPrivateBattleHistories());

			if (onlinePrivateGamesToDownload.size() > 0) {
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

			if (salmonShiftsToDownload.size() > 0) {
				String filename = String.format("Salmon_List_%s.json", timeString);
				saveFile(directory.resolve(filename), salmonListResponse);
			}

			for (String salmonShiftId : salmonShiftsToDownload) {
				String salmonShiftJson = requestSender.queryS3Api(account, S3RequestKey.SalmonDetail.getKey(), salmonShiftId);
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
		String matchJson = requestSender.queryS3Api(account, S3RequestKey.GameDetail.getKey(), matchId);
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

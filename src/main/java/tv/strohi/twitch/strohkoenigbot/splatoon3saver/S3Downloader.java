package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class S3Downloader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private Instant lastSuccessfulAttempt = Instant.now().minus(1, ChronoUnit.HOURS);

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private S3RequestSender requestSender;

	@Autowired
	public void setRequestSender(S3RequestSender requestSender) {
		this.requestSender = requestSender;
	}

	@Scheduled(cron = "30 35 * * * *")
//	@Scheduled(cron = "30 * * * * *")
	public void downloadStuffExceptionSafe() {
		sendLogs("Loading Splatoon 3 games...");
		try {
			downloadStuff();
		} catch (Exception e) {
			try {
				sendLogs(String.format("An exception occurred during S3 download: '%s'\nSee logs for details!", e.getMessage()));
			} catch (Exception ignored) {
			}

			logger.error(e);
		}
	}

	private void downloadStuff() {
//		sendLogs("Attempting to load and store Splatoon 3 results");

		String scriptFormatString = configurationRepository.findByConfigName("s3sScript").stream()
				.map(Configuration::getConfigValue)
				.findFirst()
				.orElse("python3 %s/s3s.py -o");

		List<Configuration> s3sLocations = configurationRepository.findByConfigName("s3sLocation");
		if (s3sLocations.size() == 0) return;

		Runtime rt = Runtime.getRuntime();
		for (Configuration singleS3SLocation : s3sLocations) {
			String configFileLocation = singleS3SLocation.getConfigValue();
			String completeCommand = String.format(scriptFormatString, configFileLocation).trim();
//			String[] command = completeCommand.split(" ");

			int result = -1;
			int number = 0;
			while (result != 0 && number < 5) {
				try {
					if (number > 0) {
						ConfigFile configFile = readConfigFile(configFileLocation);
						configFile.setGtoken("");
						configFile.setBullettoken("");
						storeConfigFile(configFileLocation, configFile);

						try {
							logger.warn(String.format("Didn't work, retrying & sleeping for %d seconds before the next attempt", number * 10));
							Thread.sleep(number * 10000);
						} catch (InterruptedException e) {
							logger.error(e);
						}
					}

					number++;
					result = rt.exec("git pull", null, new File(configFileLocation)).waitFor();
					result += rt.exec("pip install -r requirements.txt", null, new File(configFileLocation)).waitFor();

					if (result == 0) {
//						sendLogs(completeCommand);
//						result = rt.exec(completeCommand, null, new File(configFileLocation)).waitFor();

						ProcessBuilder ps = new ProcessBuilder(completeCommand.split(" "));
						ps.redirectErrorStream(true);
						Process process = ps.start();

						BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));

						try {
							String line;
							while ((line = in.readLine()) != null) {
								logger.info(line);
							}
						} catch (IOException ex) {
							logger.error(ex);
						}

						result = process.waitFor();

						in.close();
					} else {
						sendLogs(String.format("Result was %d before the import even started!", result));
					}
				} catch (IOException | InterruptedException e) {
					sendLogs("Exception while executing s3s process, see logs!");
					logger.error(e);
				}
			}

			if (result != 0) {
				logger.warn("Did not work..");
				if (lastSuccessfulAttempt.isBefore(Instant.now().minus(3, ChronoUnit.HOURS))) {
					sendLogs("Exception while executing s3s process!! Result wasn't 0 for at least three hours now!");
				}

				continue;
			}

			lastSuccessfulAttempt = Instant.now();

			ConfigFile configFile = readConfigFile(configFileLocation);

			if (configFile == null) continue;

			String gToken = configFile.gtoken;
			String bulletToken = configFile.bullettoken;

			String accountUUIDHash = UUID.nameUUIDFromBytes(Path.of(configFileLocation, "config.txt").toString().getBytes()).toString();
			Path directory = Path.of("game-results", accountUUIDHash);
			if (!Files.exists(directory)) {
				try {
					Files.createDirectories(directory);
				} catch (IOException e) {
					sendLogs(String.format("Could not create game directory!! %s", directory));
					continue;
				}
			}

			File battleOverviewFile = directory.resolve("Already_Downloaded_Battles.json").toFile();
			DownloadedGameList allDownloadedGames;
			try {
				if (battleOverviewFile.exists() && Files.size(battleOverviewFile.toPath()) > 0) { // if file already exists will do nothing
					allDownloadedGames = objectMapper.readValue(battleOverviewFile, DownloadedGameList.class);
				} else if (battleOverviewFile.exists() || battleOverviewFile.createNewFile()) {
					allDownloadedGames = new DownloadedGameList(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
					objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
				} else {
					sendLogs("COULD NOT OPEN FILE!!!");
					continue;
				}
			} catch (IOException e) {
				sendLogs("IOEXCEPTION WHILE OPENING OR WRITING OVERVIEW FILE!!!");
				logger.error(e);
				continue;
			}

			String homeResponse = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.Home.getKey());
			logger.debug(homeResponse);

			if (!homeResponse.contains("currentPlayer")) {
				sendLogs("Could not load homepage from SplatNet3");
				continue;
			}

			ZonedDateTime now = Instant.now().atZone(ZoneId.systemDefault());
			String timeString = String.format("%04d-%02d-%02d_%02d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond());

			List<String> onlineRegularGamesToDownload = new ArrayList<>();
			List<String> onlineAnarchyGamesToDownload = new ArrayList<>();
			List<String> onlinePrivateGamesToDownload = new ArrayList<>();
			for (S3RequestKey key : S3RequestKey.getOnlineBattles()) {
				String gameListResponse = requestSender.queryS3Api(bulletToken, gToken, key.getKey());
				logger.debug(gameListResponse);
				if (!gameListResponse.contains("assistAverage")) {
					sendLogs(String.format("Could not load results from SplatNet3: %s", key));
					continue;
				}

				BattleResults parsedResult;
				try {
					parsedResult = objectMapper.readValue(gameListResponse, BattleResults.class);
				} catch (JsonProcessingException e) {
					sendLogs(String.format("Could not parse results from SplatNet3: %s", key));
					continue;
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

			for (String matchId : onlineRegularGamesToDownload) {
				storeOnlineGame(gToken, bulletToken, "Regular", directory, allDownloadedGames.getRegular_games(), matchId);
			}

			for (String matchId : onlineAnarchyGamesToDownload) {
				storeOnlineGame(gToken, bulletToken, "Anarchy", directory, allDownloadedGames.getAnarchy_games(), matchId);
			}

			for (String matchId : onlinePrivateGamesToDownload) {
				storeOnlineGame(gToken, bulletToken, "Private", directory, allDownloadedGames.getPrivate_games(), matchId);
			}

			String salmonListResponse = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.Salmon.getKey());
			logger.debug(salmonListResponse);

			List<String> salmonShiftsToDownload = new ArrayList<>();
			if (salmonListResponse.contains("coop")) {
				BattleResults parsedResult = null;
				try {
					parsedResult = objectMapper.readValue(salmonListResponse, BattleResults.class);
				} catch (JsonProcessingException e) {
					sendLogs("Could not parse results from SplatNet3: Salmon Run");
				}

				if (parsedResult != null) {
					storeIdsOfMatchesToDownload(allDownloadedGames.getSalmon_games(), salmonShiftsToDownload, parsedResult.getData().getCoopResult());

					if (salmonShiftsToDownload.size() > 0) {
						String filename = String.format("Salmon_List_%s.json", timeString);
						saveFile(directory.resolve(filename), salmonListResponse);
					}

					for (String salmonShiftId : salmonShiftsToDownload) {
						String salmonShiftJson = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.SalmonDetail.getKey(), salmonShiftId);
						logger.debug(salmonShiftJson);

						if (!salmonShiftJson.contains("coopHistoryDetail")) {
							sendLogs("could not load match detail from splatnet!");
							continue;
						}

						String filename = String.format("Salmon_Result_%05d.json", allDownloadedGames.getSalmon_games().size() + 1);
						if (saveFile(directory.resolve(filename), salmonShiftJson)) {
							try {
								SingleMatchResult data = objectMapper.readValue(salmonShiftJson, SingleMatchResult.class);

								allDownloadedGames.getSalmon_games().put(salmonShiftId, new StoredGame(
										allDownloadedGames.getSalmon_games().size() + 1,
										filename,
										Instant.parse(data.getData().getCoopHistoryDetail().getPlayedTime())));
							} catch (JsonProcessingException e) {
								sendLogs("Could not parse single salmon shift result!");
								logger.error(e);
							}
						}
					}
				}
			} else {
				sendLogs("Could not load Salmon Run Stats from SplatNet3");
			}

			try {
				objectMapper.writeValue(battleOverviewFile, allDownloadedGames);
			} catch (IOException e) {
				sendLogs("IOEXCEPTION WHILE WRITING REFRESHED OVERVIEW FILE!!!");
				logger.error(e);
			}

			File file = new File(".");
			List<String> directories = Arrays.stream(Objects.requireNonNull(file.list((current, name) -> new File(current, name).isDirectory())))
					.filter(name -> name.startsWith("export-"))
					.collect(Collectors.toList());

			if (onlineRegularGamesToDownload.size() > 0
					|| onlineAnarchyGamesToDownload.size() > 0
					|| onlinePrivateGamesToDownload.size() > 0
					|| salmonShiftsToDownload.size() > 0) {
				// move exported folders to back up directory
				for (String dir : directories) {
					try {
						Files.move(new File(dir).toPath(), directory.resolve(dir), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						sendLogs(String.format("could not move directory %s", dir));
					}
				}
			} else {
				// delete exported folders
				for (String dir : directories) {
					try {
						FileSystemUtils.deleteRecursively(Path.of(dir));
					} catch (IOException e) {
						sendLogs(String.format("could not delete directory %s", dir));
						logger.error(e);
					}
				}
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

				sendLogs(message);
			}
		}
	}

	private ConfigFile readConfigFile(String configFileDirectory) {
		Path configFileLocation = Path.of(configFileDirectory, "config.txt");
		if (Files.exists(configFileLocation)) {
			try {
				return objectMapper.readValue(configFileLocation.toUri().toURL(), ConfigFile.class);
			} catch (IOException e) {
				sendLogs("Exception while loading config file, see logs!");
				logger.error(e);
			}
		} else {
			sendLogs("Config file does not exist");
		}

		return new ConfigFile();
	}

	private void storeConfigFile(String configFileDirectory, ConfigFile configFile) {
		Path configFileLocation = Path.of(configFileDirectory, "config.txt");
		try {
			objectMapper.writeValue(configFileLocation.toFile(), configFile);
		} catch (IOException e) {
			sendLogs("Could not store new config file on disk");
		}
	}

	private void storeOnlineGame(String gToken, String bulletToken, String filenamePrefix, Path directory, Map<String, StoredGame> games, String matchId) {
		String matchJson = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.GameDetail.getKey(), matchId);
		logger.debug(matchJson);

		if (!matchJson.contains("vsHistoryDetail")) {
			sendLogs("could not load match detail from splatnet!");
		}

		String filename = String.format("%s_Result_%05d.json", filenamePrefix, games.size() + 1);
		if (saveFile(directory.resolve(filename), matchJson)) {
			try {
				SingleMatchResult data = objectMapper.readValue(matchJson, SingleMatchResult.class);

				games.put(matchId, new StoredGame(
						games.size() + 1,
						filename,
						Instant.parse(data.getData().getVsHistoryDetail().getPlayedTime())));
			} catch (JsonProcessingException e) {
				sendLogs("Could not parse single match result!");
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
				sendLogs(String.format("Could not create directory for file! %s", path));
				return false;
			}
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toAbsolutePath().toFile()))) {
			writer.write(content);
			return true;
		} catch (IOException e) {
			sendLogs(String.format("Could not write file! %s", path));
		}

		return false;
	}

	private void storeIdsOfMatchesToDownload(Map<String, StoredGame> allDownloadedGames, List<String> idsOfMatchesToDownload, BattleHistories battleHistories) {
		for (HistoryGroupsNodes historyGroupNode : battleHistories.getHistoryGroups().getNodes()) {
			for (HistoryGroupMatch singleMatch : historyGroupNode.getHistoryDetails().getNodes()) {
				if (!allDownloadedGames.containsKey(singleMatch.getId()) && !idsOfMatchesToDownload.contains(singleMatch.getId())) {
					idsOfMatchesToDownload.add(0, singleMatch.getId());
				}
			}
		}
	}

	private void sendLogs(String message) {
		logger.debug(message);
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
	}

	@Getter
	@Setter
	public static class ConfigFile {
		private String acc_loc;
		private String api_key;
		private String bullettoken;
		private String f_gen;
		private String gtoken;
		private String session_token;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DownloadedGameList {
		private Map<String, StoredGame> regular_games;
		private Map<String, StoredGame> anarchy_games;
		private Map<String, StoredGame> private_games;
		private Map<String, StoredGame> salmon_games;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class StoredGame {
		private int number;
		private String filename;
		private Instant startDate;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleResults {
		private BattleResultsData data;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleResultsData {
		private BattleHistories latestBattleHistories;
		private BattleHistories regularBattleHistories;
		private BattleHistories bankaraBattleHistories;
		private BattleHistories privateBattleHistories;
		private BattleHistories coopResult;
		private Object currentFest;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleHistories {
		private Object summary;
		private Object historyGroupsOnlyFirst;
		private HistoryGroups historyGroups;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroups {
		private HistoryGroupsNodes[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodes {
		private HistoryGroupsNodesDetails historyDetails;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodesDetails {
		private HistoryGroupMatch[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResult {
		private SingleMatchResultData data;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResultData {
		private HistoryGroupMatch vsHistoryDetail;
		private HistoryGroupMatch coopHistoryDetail;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupMatch {
		private String id;
		private String playedTime;
	}
}

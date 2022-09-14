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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class S3Downloader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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

	@Scheduled(cron = "30 7 * * * *")
	public void downloadStuff() {
		sendLogs("Attempting to load and store Splatoon 3 results");

		List<Configuration> s3sScripts = configurationRepository.findByConfigName("s3sScript");
		List<Configuration> s3sLocations = configurationRepository.findByConfigName("s3sLocation");
		if (s3sLocations.size() == 0) return;

		for (Configuration singleS3SScript : s3sScripts) {
			Runtime rt = Runtime.getRuntime();
			try {
				int result = rt.exec(singleS3SScript.getConfigValue()).waitFor();

				if (result != 0) {
					sendLogs(String.format("Exception while executing s3s process!! Result wasn't 0 but %d instead!", result));
				}
			} catch (IOException | InterruptedException e) {
				sendLogs("Exception while executing s3s process, see logs!");
				logger.error(e);
			}
		}

		for (Configuration singleS3SLocation : s3sLocations) {
			String gToken = null;
			String bulletToken = null;

			Path configFileLocation = Path.of(singleS3SLocation.getConfigValue(), "config.txt");
			if (Files.exists(configFileLocation)) {
				ConfigFile configFileContent;
				try {
					configFileContent = objectMapper.readValue(configFileLocation.toUri().toURL(), ConfigFile.class);
					gToken = configFileContent.gtoken;
					bulletToken = configFileContent.bullettoken;
				} catch (IOException e) {
					sendLogs("Exception while loading s3s gToken from config file, see logs!");
					logger.error(e);
				}
			} else {
				sendLogs("Config file does not exist, see logs!");
			}

			if (gToken == null || bulletToken == null) continue;

			String accountUUIDHash = UUID.nameUUIDFromBytes(configFileLocation.toString().getBytes()).toString();
			Path directory = Path.of("game-results", accountUUIDHash);
			if (!Files.exists(directory)) {
				try {
					Files.createDirectories(directory);
				} catch (IOException e) {
					sendLogs(String.format("Could not create game directory!! %s", directory));
					continue;
				}
			}

			File battleOverviewFile = directory.resolve("downloaded-battles.json").toFile();
			DownloadedGameList allDownloadedGames;
			try {
				if (battleOverviewFile.exists() && Files.size(battleOverviewFile.toPath()) > 0) { // if file already exists will do nothing
					allDownloadedGames = objectMapper.readValue(battleOverviewFile, DownloadedGameList.class);
				} else if (battleOverviewFile.exists() || battleOverviewFile.createNewFile()) {
					allDownloadedGames = new DownloadedGameList(0, 0, new HashMap<>(), new HashMap<>());
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

			String body = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.Home.getKey());
			logger.debug(body);

			if (!body.contains("currentPlayer")) {
				sendLogs("Could not load homepage from SplatNet3");
				continue;
			}

			ZonedDateTime now = Instant.now().atZone(ZoneId.systemDefault());
			String timeString = String.format("%04d-%02d-%02d_%02d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond());

			List<String> onlineGamesToDownload = new ArrayList<>();
			for (S3RequestKey key : S3RequestKey.getOnlineBattles()) {
				body = requestSender.queryS3Api(bulletToken, gToken, key.getKey());
				logger.debug(body);
				if (!body.contains("assistAverage")) {
					sendLogs(String.format("Could not load results from SplatNet3: %s", key));
					continue;
				}

				String filename = String.format("%s_%s.json", key, timeString);
				saveFile(directory.resolve(filename), body);

				BattleResults parsedResult;
				try {
					parsedResult = objectMapper.readValue(body, BattleResults.class);
				} catch (JsonProcessingException e) {
					sendLogs(String.format("Could not parse results from SplatNet3: %s", key));
					continue;
				}

				logger.debug(parsedResult);

				// Eventuell auch die latest results pullen?
				// Aktuell nicht umgesetzt, da selbe matches unterschiedliche IDs haben in den unterschiedlichen Listen
				if (parsedResult.getData().getRegularBattleHistories() != null) {
					storeIdsOfMatchesToDownload(allDownloadedGames.getGames(), onlineGamesToDownload, parsedResult.getData().getRegularBattleHistories());
				}

				if (parsedResult.getData().getBankaraBattleHistories() != null) {
					storeIdsOfMatchesToDownload(allDownloadedGames.getGames(), onlineGamesToDownload, parsedResult.getData().getBankaraBattleHistories());
				}

				if (parsedResult.getData().getPrivateBattleHistories() != null) {
					storeIdsOfMatchesToDownload(allDownloadedGames.getGames(), onlineGamesToDownload, parsedResult.getData().getPrivateBattleHistories());
				}

				logger.debug(onlineGamesToDownload);
			}

			for (String matchId : onlineGamesToDownload) {
				String matchJson = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.GameDetail.getKey(), matchId);
				logger.debug(matchJson);

				if (!matchJson.contains("vsHistoryDetail")) {
					sendLogs("could not load match detail from splatnet!");
				}

				String filename = String.format("Battle_Result_%05d.json", allDownloadedGames.getNumber_of_games() + 1);
				if (saveFile(directory.resolve(filename), matchJson)) {
					try {
						SingleMatchResult data = objectMapper.readValue(matchJson, SingleMatchResult.class);

						allDownloadedGames.setNumber_of_games(allDownloadedGames.getNumber_of_games() + 1);
						allDownloadedGames.getGames().put(matchId, new StoredGame(
								allDownloadedGames.getNumber_of_games(),
								filename,
								Instant.parse(data.getData().getVsHistoryDetail().getPlayedTime())));
					} catch (JsonProcessingException e) {
						sendLogs("Could not parse single match result!");
						logger.error(e);
					}
				}
			}

			body = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.Salmon.getKey());
			logger.debug(body);

			List<String> salmonShiftsToDownload = new ArrayList<>();
			if (body.contains("coop")) {
				String filename = String.format("Salmon_%s.json", timeString);
				saveFile(directory.resolve(filename), body);

				BattleResults parsedResult = null;
				try {
					parsedResult = objectMapper.readValue(body, BattleResults.class);
				} catch (JsonProcessingException e) {
					sendLogs("Could not parse results from SplatNet3: Salmon Run");
				}

				if (parsedResult != null) {
					storeIdsOfMatchesToDownload(allDownloadedGames.getSalmon_games(), salmonShiftsToDownload, parsedResult.getData().getCoopResult());

					for (String salmonShiftId : salmonShiftsToDownload) {
						String salmonShiftJson = requestSender.queryS3Api(bulletToken, gToken, S3RequestKey.SalmonDetail.getKey(), salmonShiftId);
						logger.debug(salmonShiftJson);

						if (!salmonShiftJson.contains("coopHistoryDetail")) {
							sendLogs("could not load match detail from splatnet!");
							continue;
						}

						filename = String.format("Salmon_Result_%05d.json", allDownloadedGames.getNumber_of_salmon_games() + 1);
						if (saveFile(directory.resolve(filename), salmonShiftJson)) {
							try {
								SingleMatchResult data = objectMapper.readValue(salmonShiftJson, SingleMatchResult.class);

								allDownloadedGames.setNumber_of_salmon_games(allDownloadedGames.getNumber_of_salmon_games() + 1);
								allDownloadedGames.getSalmon_games().put(salmonShiftId, new StoredGame(
										allDownloadedGames.getNumber_of_salmon_games(),
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

			if (onlineGamesToDownload.size() > 0 || salmonShiftsToDownload.size() > 0) {
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

			sendLogs(String.format("Finished loading and storing Splatoon 3 results:\n- **%d** new pvp matches\n- **%d** new salmon run shifts",
					onlineGamesToDownload.size(),
					salmonShiftsToDownload.size()));
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
		private int number_of_games;
		private int number_of_salmon_games;
		private Map<String, StoredGame> games;
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

package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.EnemyResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3DailyStatsSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;

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

		Map<String, Integer> wonOnlineGames = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getAnarchy_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames);
		}
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getX_rank_games().entrySet()) {
			countOnlineWins(game, directory, wonOnlineGames);
		}

		var winStats = wonOnlineGames.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder winBuilder = new StringBuilder("**Current Online Game Win statistics:**");

		for (var srEnemyStat : winStats) {
			winBuilder.append("\n- ").append(srEnemyStat.getKey()).append(": **").append(srEnemyStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), winBuilder.toString());

		Map<String, Integer> defeatedSalmonRunBosses = new HashMap<>();
		for (Map.Entry<String, ConfigFile.StoredGame> game : allDownloadedGames.getSalmon_games().entrySet()) {
			countSalmonRunEnemyDefeatResults(game, directory, defeatedSalmonRunBosses);
		}

		var srEnemyStats = defeatedSalmonRunBosses.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		StringBuilder srBossBuilder = new StringBuilder("**Current Salmon Run Boss Kill statistics:**");

		for (var srEnemyStat : srEnemyStats) {
			srBossBuilder.append("\n- ").append(srEnemyStat.getKey()).append(": **").append(srEnemyStat.getValue()).append("**");
		}

		discordBot.sendPrivateMessage(account.getDiscordId(), srBossBuilder.toString());

		logger.info("Done with loading Splatoon 3 games for account with folder name '{}'...", folderName);
	}

	private void countSalmonRunEnemyDefeatResults(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> defeatedSalmonRunBosses) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);

			for (EnemyResults enemyResult : result.getData().getCoopHistoryDetail().getEnemyResults()) {
				int currentCount = defeatedSalmonRunBosses.getOrDefault(enemyResult.getEnemy().getName(), 0);
				defeatedSalmonRunBosses.put(enemyResult.getEnemy().getName(), currentCount + enemyResult.getDefeatCount());
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}

	private void countOnlineWins(Map.Entry<String, ConfigFile.StoredGame> game, Path directory, Map<String, Integer> winResults) {
		String filename = directory.resolve(game.getValue().getFilename()).toAbsolutePath().toString();

		try {
			logger.info(filename);
			BattleResult result = objectMapper.readValue(new File(filename), BattleResult.class);
			logger.debug(result);

			if ("WIN".equals(result.getData().getVsHistoryDetail().getJudgement())) {
				int currentCount = winResults.getOrDefault(result.getData().getVsHistoryDetail().getVsRule().getName(), 0);
				winResults.put(result.getData().getVsHistoryDetail().getVsRule().getName(), currentCount + 1);
			}
		} catch (IOException e) {
			logSender.sendLogs(logger, String.format("Couldn't parse salmon run result json file '%s' OH OH", filename));
			logger.error(e);
		}
	}
}

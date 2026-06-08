package tv.strohi.twitch.strohkoenigbot.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3ApiQuerySender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/bot")
@Log4j2
public class BotController implements ScheduledService {
	private final String BEELINK_BOOT_MESSAGE_CONFIG = "BotController_BeelinkMessageTime";

	private final Bucket adminMessageBucket;
	private final Bucket loadLatestGameStringBucket;

	private final LogSender logSender;
	private final S3Downloader s3Downloader;
	private final TwitchBotClient twitchBotClient;

	private final S3ApiQuerySender s3ApiQuerySender;
	private final ObjectMapper mapper;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsModeRepository modeRepository;

	public BotController(LogSender logSender, S3Downloader s3Downloader, TwitchBotClient twitchBotClient, S3ApiQuerySender s3ApiQuerySender, ObjectMapper mapper, AccountRepository accountRepository, ConfigurationRepository configurationRepository, Splatoon3VsModeRepository modeRepository) {
		this.logSender = logSender;
		this.s3Downloader = s3Downloader;
		this.twitchBotClient = twitchBotClient;
		this.s3ApiQuerySender = s3ApiQuerySender;
		this.mapper = mapper;
		this.accountRepository = accountRepository;
		this.configurationRepository = configurationRepository;
		this.modeRepository = modeRepository;

		var limit = Bandwidth.builder()
			.capacity(10)
			.refillGreedy(10, Duration.ofMinutes(1))
			.build();

		this.adminMessageBucket = Bucket.builder()
			.addLimit(limit)
			.build();

		this.loadLatestGameStringBucket = Bucket.builder()
			.addLimit(Bandwidth.builder()
				.capacity(5)
				.refillGreedy(5, Duration.ofMinutes(1))
				.build())
			.build();
	}

	@GetMapping()
	public BotStatus getBotStatus() {
		BotStatus status = new BotStatus();

		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());
		status.setRunning(twitchBotClient.isLive(account.getTwitchUserId()));

		return status;
	}

	@GetMapping("live-time")
	public long getLiveTime() {
		if (twitchBotClient.getWentLiveTime() != null) {
			return Duration.between(twitchBotClient.getWentLiveTime(), Instant.now()).toSeconds();
		}

		return 0L;
	}

	@GetMapping("latest-game-string")
	public String getLatestGameString() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		if (loadLatestGameStringBucket.tryConsume(1) && twitchBotClient.getWentLiveTime() != null) {
			try {
				var latestGamesResponse = s3ApiQuerySender.queryS3Api(account, S3RequestKey.Latest);
				var latestGames = mapper.readValue(latestGamesResponse, BattleResults.class);

				var latestGame = Arrays.stream(latestGames.getData().getLatestBattleHistories().getHistoryGroups().getNodes())
					.findFirst()
					.stream()
					.flatMap(hgn -> Arrays.stream(hgn.getHistoryDetails().getNodes()))
					.findFirst()
					.orElse(null);

				if (latestGame != null) {
					return String.format("%s - %s - %s - %s - %s",
						latestGame.getJudgement(),
						latestGame.getPlayer().getWeapon().getName(),
						modeRepository.findByApiId(latestGame.getVsMode().getId())
							.map(Splatoon3VsMode::getName)
							.orElse("Unknown Mode"),
						latestGame.getVsRule().getName(),
						latestGame.getVsStage().getName());
				}
			} catch (JsonProcessingException ignored) {
			}
		}

		return "";
	}

	@PostMapping("start")
	public void startExporter() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		if (!twitchBotClient.isLive(account.getTwitchUserId())) {
			twitchBotClient.goLive(account.getTwitchUserId());
		}
	}

	@PostMapping("import")
	public void importGames() {
		log.info("Instance `{}`, debug = `{}` is importing games triggered by BotController", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());
		s3Downloader.downloadBattles(true);
	}

	@PostMapping("stop")
	public void stopExporter() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		twitchBotClient.goOffline(account.getTwitchUserId());
	}

	@PostMapping("admin-message")
	public ResponseEntity<Void> sendAdminMessage(@RequestBody @NonNull String message) {
		if (!adminMessageBucket.tryConsume(1)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}

		if (message.length() > 1800) {
			ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		if ("Beelink booted successfully.".equals(message)) {
			var config = configurationRepository.findByConfigName(BEELINK_BOOT_MESSAGE_CONFIG)
				.orElseGet(() -> configurationRepository.save(Configuration.builder()
					.configName(BEELINK_BOOT_MESSAGE_CONFIG)
					.configValue(String.format("%d", Instant.now().getEpochSecond()))
					.build()));

			config.setConfigValue(String.format("%d", Instant.now().getEpochSecond()));
			configurationRepository.save(config);
		} else {
			logSender.queueLogs(log, String.format("Instance `%s`, debug = `%s` received message via web interface:\n```\n%s\n```", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), message));
		}

		return ResponseEntity.ok().build();
	}

	private void checkBeelinkActivity() {
		var config = configurationRepository.findByConfigName(BEELINK_BOOT_MESSAGE_CONFIG)
			.orElseGet(() -> configurationRepository.save(Configuration.builder()
				.configName(BEELINK_BOOT_MESSAGE_CONFIG)
				.configValue(String.format("%d", Instant.now().getEpochSecond()))
				.build()));

		var lastMessageTimestamp = Instant.ofEpochSecond(Long.parseLong(config.getConfigValue()));

		if (Instant.now().isAfter(lastMessageTimestamp.plus(6, ChronoUnit.HOURS))) {
			logSender.queueLogs(log, String.format("## WARNING\nInstance `%s`, debug = `%s` did **not** receive a message from Beelink for 6 hours! Last message EpochSecond: `%s`", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), config.getConfigValue()));
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("BotController_checkBeelinkTiming")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyHours(6)))
			.runnable(this::checkBeelinkActivity)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

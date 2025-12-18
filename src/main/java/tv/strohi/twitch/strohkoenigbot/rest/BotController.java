package tv.strohi.twitch.strohkoenigbot.rest;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/bot")
public class BotController implements ScheduledService {
	private final String BEELINK_BOOT_MESSAGE_CONFIG = "BotController_BeelinkMessageTime";

	private final Bucket bucket;

	private final DiscordBot discordBot;
	private final S3Downloader s3Downloader;
	private final TwitchBotClient twitchBotClient;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	public BotController(DiscordBot discordBot, S3Downloader s3Downloader, TwitchBotClient twitchBotClient, AccountRepository accountRepository, ConfigurationRepository configurationRepository) {
		this.discordBot = discordBot;
		this.s3Downloader = s3Downloader;
		this.twitchBotClient = twitchBotClient;
		this.accountRepository = accountRepository;
		this.configurationRepository = configurationRepository;

		var limit = Bandwidth.builder()
			.capacity(10)
			.refillGreedy(10, Duration.ofMinutes(1))
			.build();

		this.bucket = Bucket.builder()
			.addLimit(limit)
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
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, String.format("Instance `%s`, debug = `%s` is importing games triggered by BotController", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug()));
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
		if (!bucket.tryConsume(1)) {
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
			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, String.format("Instance `%s`, debug = `%s` received message via web interface:\n```\n%s\n```", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), message));
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
			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, String.format("## WARNING\nInstance `%s`, debug = `%s` did **not** receive a message from Beelink for 6 hours! Last message EpochSecond: `%s`", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), config.getConfigValue()));
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

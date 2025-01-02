package tv.strohi.twitch.strohkoenigbot.rest;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.Duration;

@RestController
@RequestMapping("/bot")
public class BotController {
	private final Bucket bucket;

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private TwitchBotClient twitchBotClient;

	@Autowired
	public void setTwitchBotClient(TwitchBotClient twitchBotClient) {
		this.twitchBotClient = twitchBotClient;
	}

	private ResultsExporter resultsExporter;

	@Autowired
	public void setResultsExporter(ResultsExporter resultsExporter) {
		this.resultsExporter = resultsExporter;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public BotController() {
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
			twitchBotClient.setFakeDebug(true);
			resultsExporter.start(account);
		}
	}

	@PostMapping("stop")
	public void stopExporter() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		if (twitchBotClient.isLive(account.getTwitchUserId())) {
			twitchBotClient.setFakeDebug(false);
			resultsExporter.stop(account);
		}
	}

	@PostMapping("admin-message")
	public ResponseEntity<Void> sendAdminMessage(@RequestBody @NonNull String message) {
		if (bucket.tryConsume(1)) {
			if (message.length() <= 1800) {
				discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, String.format("Instance `%s`, debug = `%s` received message via web interface:\n```\n%s\n```", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), message));
				return ResponseEntity.ok().build();
			} else {
				ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
			}
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}
}

package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

@RestController
@RequestMapping("/bot")
public class BotController {
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

	@GetMapping()
	public BotStatus getBotStatus() {
		BotStatus status = new BotStatus();

		Account account = accountRepository.findAll().stream()
				.filter(Account::getIsMainAccount)
				.findFirst()
				.orElse(new Account());
		status.setRunning(twitchBotClient.isLive(account.getTwitchUserId()));

		return status;
	}

	@PostMapping("start")
	public void startExporter() {
		Account account = accountRepository.findAll().stream()
				.filter(Account::getIsMainAccount)
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
				.filter(Account::getIsMainAccount)
				.findFirst()
				.orElse(new Account());

		if (twitchBotClient.isLive(account.getTwitchUserId())) {
			twitchBotClient.setFakeDebug(false);
			resultsExporter.stop(account);
		}
	}
}

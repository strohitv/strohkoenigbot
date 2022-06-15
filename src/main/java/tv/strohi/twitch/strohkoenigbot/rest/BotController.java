package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

@RestController
@RequestMapping("/bot")
public class BotController {
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

		status.setRunning(resultsExporter.isStreamRunning());

		return status;
	}

	@PostMapping("start")
	public void startExporter() {
		if (!resultsExporter.isStreamRunning()) {
			Account account = accountRepository.findAll().stream()
					.filter(Account::getIsMainAccount)
					.findFirst()
					.orElse(new Account());

			resultsExporter.start(account.getId());
		}
	}

	@PostMapping("stop")
	public void stopExporter() {
		if (resultsExporter.isStreamRunning()) {
			resultsExporter.stop();
		}
	}
}

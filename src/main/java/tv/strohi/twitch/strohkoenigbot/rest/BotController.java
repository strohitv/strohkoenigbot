package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

@RestController("/bot")
public class BotController {
	private ResultsExporter resultsExporter;

	@Autowired
	public void setResultsExporter(ResultsExporter resultsExporter) {
		this.resultsExporter = resultsExporter;
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
			resultsExporter.start();
		}
	}

	@PostMapping("stop")
	public void stopExporter() {
		if (resultsExporter.isStreamRunning()) {
			resultsExporter.stop();
		}
	}
}

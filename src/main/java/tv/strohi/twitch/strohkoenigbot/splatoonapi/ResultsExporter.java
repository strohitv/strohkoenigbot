package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMatchResultsCollection;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.Statistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Statistics statistics = new Statistics("C:\\Users\\marco\\Documents\\code\\java\\quagsirebot\\src\\main\\resources\\html\\template-example.html");

	private boolean running = false;
	private boolean alreadyRunning = false;
	private Instant lastAnalysedMatchStart;

	public void start(Instant startTime) {
		this.running = true;
		lastAnalysedMatchStart = startTime;
		statistics.reset();
	}

	public void stop() {
		this.running = false;
	}

	public void setLastAnalysedMatchStart(Instant lastAnalysedMatchStart) {
		this.lastAnalysedMatchStart = lastAnalysedMatchStart;
	}

	private ResultsLoader splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(ResultsLoader splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	@Scheduled(fixedRate = 15000)
	public void loadGameResultsScheduled() {
		if (running && !alreadyRunning) {
			alreadyRunning = true;

			SplatoonMatchResultsCollection collection = splatoonResultsLoader.getGameResults();

			if (lastAnalysedMatchStart == null) {
				lastAnalysedMatchStart = Instant.now();
			}

			List<SplatoonMatchResultsCollection.SplatoonMatchResult> results = new ArrayList<>();
			for (int i = collection.getResults().length - 1; i >= 0; i--) {
				results.add(collection.getResults()[i]);
			}

			results = results.stream()
					.filter(r -> r.getStartTimeAsInstant().isAfter(lastAnalysedMatchStart))
					.collect(Collectors.toList());

			statistics.addMatches(results);

			if (results.size() > 0) {
				lastAnalysedMatchStart = results.get(results.size() - 1).getStartTimeAsInstant();
			}

			statistics.exportHtml();

			alreadyRunning = false;
		}
	}
}

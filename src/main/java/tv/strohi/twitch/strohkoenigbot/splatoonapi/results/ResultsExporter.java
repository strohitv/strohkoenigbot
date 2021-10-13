package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.PlayerLeaderboardPeaksLoader;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMatchResultsCollection;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonPlayerPeaks;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.Statistics;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Statistics statistics;

	public ResultsExporter() {
		String path = Paths.get(".").toAbsolutePath().normalize().toString();
		statistics = new Statistics(String.format("%s\\src\\main\\resources\\html\\template-example.html", path));
	}

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
		statistics.stop();
	}

	public void setLastAnalysedMatchStart(Instant lastAnalysedMatchStart) {
		this.lastAnalysedMatchStart = lastAnalysedMatchStart;
	}

	private ResultsLoader splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(ResultsLoader splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private PlayerLeaderboardPeaksLoader peaksLoader;

	@Autowired
	public void setPeaksLoader(PlayerLeaderboardPeaksLoader peaksLoader) {
		this.peaksLoader = peaksLoader;
	}

	private boolean peaksLoaded = false;
	private SplatoonPlayerPeaks peaks;

	@Scheduled(fixedRate = 15000, initialDelay = 5000)
	public void loadGameResultsScheduled() {
//		if (!peaksLoaded) {
//			peaks = peaksLoader.getPlayerPeaks();
//			peaksLoaded = true;
//		}

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

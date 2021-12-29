package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMonthlyResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMonthlyResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResultsCollection;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.Statistics;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Statistics statistics;

	public ResultsExporter() {
		String path = Paths.get(".").toAbsolutePath().normalize().toString();
		statistics = new Statistics(String.format("%s\\src\\main\\resources\\html\\template-example.html", path));
	}

	private boolean alreadyRunning = false;
	private boolean isStreamRunning = false;
	private Instant lastAnalysedMatchStart = Instant.now().minus(2, ChronoUnit.MINUTES);

	public void start() {
		isStreamRunning = true;
		statistics.reset();
	}

	public void stop() {
		isStreamRunning = false;
		statistics.stop();
	}

	public void setLastAnalysedMatchStart(Instant lastAnalysedMatchStart) {
		this.lastAnalysedMatchStart = lastAnalysedMatchStart;
	}

	private RequestSender splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(RequestSender splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private SplatoonMonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(SplatoonMonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public String getHtml() {
		return statistics.getCurrentHtml();
	}

	@Scheduled(fixedRate = 15000, initialDelay = 90000)
	public void loadGameResultsScheduled() {
		if (!alreadyRunning) {
			alreadyRunning = true;

			SplatNetMatchResultsCollection collection = splatoonResultsLoader.querySplatoonApi("/api/results", SplatNetMatchResultsCollection.class);

			List<SplatNetMatchResultsCollection.SplatNetMatchResult> results = new ArrayList<>();
			for (int i = collection.getResults().length - 1; i >= 0; i--) {
				results.add(collection.getResults()[i]);
			}

			// TODO: Sobald ich die Datenbanktabelle für Matches habe: statt nach lastAnalysedMatchStart
			// TODO: nach den Match-Keys filtern, die NICHT in der Tabelle enthalten sind.
			results = results.stream()
					.filter(r -> r.getStartTimeAsInstant().isAfter(lastAnalysedMatchStart))
					.collect(Collectors.toList());

			if (results.size() > 0) {
				lastAnalysedMatchStart = results.get(results.size() - 1).getStartTimeAsInstant();
			}

			if (isStreamRunning) {
				statistics.addMatches(results);
				statistics.exportHtml();
			}

			refreshMonthlyRankedResults(results);

			alreadyRunning = false;
		}
	}

	private void refreshMonthlyRankedResults(List<SplatNetMatchResultsCollection.SplatNetMatchResult> results) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault()).minus(5, ChronoUnit.DAYS);
		int year = date.getYear();
		int month = date.getMonthValue();

		SplatoonMonthlyResult result = monthlyResultRepository.findByPeriodYearAndPeriodMonth(year, month);

		if (result != null) {
			boolean isDirty = false;

			List<SplatNetMatchResultsCollection.SplatNetMatchResult> rankedMatches = results.stream()
					.filter(r -> SplatoonMode.getModeByName(r.getGame_mode().getKey()) == SplatoonMode.Ranked)
					.collect(Collectors.toList());

			for (SplatNetMatchResultsCollection.SplatNetMatchResult rankedMatch : rankedMatches) {
				SplatoonRule rule = SplatoonRule.getRuleByName(rankedMatch.getRule().getKey());

				if (rankedMatch.getX_power() != null) {
					switch (rule) {
						case SplatZones:
							if (!Objects.equals(result.getZonesCurrent(), rankedMatch.getX_power())) {
								result.setZonesCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Current zones power for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getZonesPeak() == null || result.getZonesPeak() < rankedMatch.getX_power()) {
								result.setZonesPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Zones peak for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesPeak()));
								isDirty = true;
							}
							break;
						case Rainmaker:
							if (!Objects.equals(result.getRainmakerCurrent(), rankedMatch.getX_power())) {
								result.setRainmakerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Current rainmaker power for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getRainmakerPeak() == null || result.getRainmakerPeak() < rankedMatch.getX_power()) {
								result.setRainmakerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Rainmaker peak for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesPeak()));
								isDirty = true;
							}
							break;
						case TowerControl:
							if (!Objects.equals(result.getTowerCurrent(), rankedMatch.getX_power())) {
								result.setTowerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Current tower power for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getTowerPeak() == null || result.getTowerPeak() < rankedMatch.getX_power()) {
								result.setTowerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Tower peak for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesPeak()));
								isDirty = true;
							}
							break;
						case ClamBlitz:
							if (!Objects.equals(result.getClamsCurrent(), rankedMatch.getX_power())) {
								result.setClamsCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Current clams power for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getClamsPeak() == null || result.getClamsPeak() < rankedMatch.getX_power()) {
								result.setClamsPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages("debug-logs",
										String.format("Clams peak for month **%d-%d** is now **%4.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesPeak()));
								isDirty = true;
							}
							break;
						default:
							discordBot.sendServerMessageWithImages("debug-logs",
									String.format("Error: received invalid rule **%s** for ranked mode.", rule));
							break;
					}
				}
			}

			if (isDirty) {
				monthlyResultRepository.save(result);
			}
		} else {
			discordBot.sendServerMessageWithImages("debug-logs", "Error: a monthly result for this month does NOT exist!");
		}
	}
}

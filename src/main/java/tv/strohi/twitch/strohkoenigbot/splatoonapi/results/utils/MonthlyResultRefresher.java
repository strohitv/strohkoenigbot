package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2MonthlyResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MonthlyResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class MonthlyResultRefresher {
	private Splatoon2MonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(Splatoon2MonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public void refreshMonthlyRankedResults(long accountId, List<SplatNetMatchResult> results) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		Splatoon2MonthlyResult result = monthlyResultRepository.findByAccountIdAndPeriodYearAndPeriodMonth(accountId, year, month);

		if (result != null) {
			boolean isDirty = false;

			List<SplatNetMatchResult> rankedMatches = results.stream()
					.filter(r -> Splatoon2Mode.getModeByName(r.getGame_mode().getKey()) == Splatoon2Mode.Ranked)
					.collect(Collectors.toList());

			for (SplatNetMatchResult rankedMatch : rankedMatches) {
				Splatoon2Rule rule = Splatoon2Rule.getRuleByName(rankedMatch.getRule().getKey());

				if (rankedMatch.getX_power() != null) {
					switch (rule) {
						case SplatZones:
							if (!Objects.equals(result.getZonesCurrent(), rankedMatch.getX_power())) {
								result.setZonesCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getCurrentPowerMessage("zones", result.getPeriodYear(), result.getPeriodMonth(), result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getZonesPeak() == null || result.getZonesPeak() < rankedMatch.getX_power()) {
								result.setZonesPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getPeakPowerMessage("Zones", result.getPeriodYear(), result.getPeriodMonth(), result.getZonesPeak()));
								isDirty = true;
							}
							break;
						case Rainmaker:
							if (!Objects.equals(result.getRainmakerCurrent(), rankedMatch.getX_power())) {
								result.setRainmakerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getCurrentPowerMessage("rainmaker", result.getPeriodYear(), result.getPeriodMonth(), result.getRainmakerCurrent()));
								isDirty = true;
							}

							if (result.getRainmakerPeak() == null || result.getRainmakerPeak() < rankedMatch.getX_power()) {
								result.setRainmakerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getPeakPowerMessage("Rainmaker", result.getPeriodYear(), result.getPeriodMonth(), result.getRainmakerPeak()));
								isDirty = true;
							}
							break;
						case TowerControl:
							if (!Objects.equals(result.getTowerCurrent(), rankedMatch.getX_power())) {
								result.setTowerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getCurrentPowerMessage("tower", result.getPeriodYear(), result.getPeriodMonth(), result.getTowerCurrent()));
								isDirty = true;
							}

							if (result.getTowerPeak() == null || result.getTowerPeak() < rankedMatch.getX_power()) {
								result.setTowerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getPeakPowerMessage("Tower", result.getPeriodYear(), result.getPeriodMonth(), result.getTowerPeak()));
								isDirty = true;
							}
							break;
						case ClamBlitz:
							if (!Objects.equals(result.getClamsCurrent(), rankedMatch.getX_power())) {
								result.setClamsCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getCurrentPowerMessage("clams", result.getPeriodYear(), result.getPeriodMonth(), result.getClamsCurrent()));
								isDirty = true;
							}

							if (result.getClamsPeak() == null || result.getClamsPeak() < rankedMatch.getX_power()) {
								result.setClamsPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getPeakPowerMessage("Clams", result.getPeriodYear(), result.getPeriodMonth(), result.getClamsPeak()));
								isDirty = true;
							}
							break;
						default:
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
									String.format("Error: received invalid rule **%s** for ranked mode.", rule));
							break;
					}
				}
			}

			if (isDirty) {
				monthlyResultRepository.save(result);
			}
		}
	}
}

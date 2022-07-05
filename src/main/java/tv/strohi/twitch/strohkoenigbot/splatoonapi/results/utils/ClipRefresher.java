package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Clip;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2ClipRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
public class ClipRefresher {
	private Splatoon2ClipRepository clipRepository;

	@Autowired
	public void setClipRepository(Splatoon2ClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public void refresh(long accountId, Splatoon2Match match, boolean loadSilently) {
		// refresh clips and send them to discord
		List<Splatoon2Clip> clips = clipRepository.getAllByAccountIdAndStartTimeIsGreaterThanAndEndTimeIsLessThan(accountId, match.getStartTime(), match.getEndTime());
		if (clips.size() > 0) {
			StringBuilder ratingsMessageBuilder = new StringBuilder("**Viewers rated my performance**:\n");

			for (Splatoon2Clip clip : clips) {
				ratingsMessageBuilder.append(String.format("\n- **%s** play - Clip: <%s> - Description: \"%s\"",
						clip.getIsGoodPlay() ? "GOOD" : "BAD",
						clip.getClipUrl(),
						clip.getDescription()));

				clip.setMatchId(match.getId());
			}

			if (!loadSilently) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), ratingsMessageBuilder.toString());
			}

			clipRepository.saveAll(clips);
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import com.github.twitch4j.events.ChannelClipCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.utils.Constants;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TwitchClipSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final TwitchBotClient twitchBotClient;
	private final DiscordBot discordBot;
	private final TwitchMessageSender twitchMessageSender;

	private final SchedulingService schedulingService;

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("TwitchClipSender_schedule", TickSchedule.getScheduleString(12), this::postClips);
	}

	public void postClips() {
		for (var channelName : Constants.ALL_TWITCH_CHANNEL_NAMES) {
			Optional<ChannelClipCreatedEvent> createdClip;
			while ((createdClip = twitchBotClient.pollCreatedClip(channelName)).isPresent()) {
				var clip = createdClip.get();
				logger.info("Posting clip {}", clip.getClip().getUrl());

				var twitchMessage = String.format("Thanks for creating a clip @%s! -> clip: %s, url: %s",
					clip.getClip().getCreatorName(),
					clip.getClip().getTitle(),
					clip.getClip().getUrl());

				twitchMessageSender.send(channelName, twitchMessage);

				var gameName = twitchBotClient.getGameName(clip.getClip().getGameId()).orElse("unknown");

				var discordMessage = String.format("**New Clip on %s**\n- Title: %s\n- Created by: %s\n- Game: %s\n- Duration: %ss\n- Stream language: %s\n- Created At: <t:%d:f> (<t:%d:R>)\n\nLink: <%s>",
					channelName,
					clip.getClip().getTitle(),
					clip.getClip().getCreatorName(),
					gameName,
					clip.getClip().getDuration(),
					clip.getClip().getLanguage(),
					clip.getClip().getCreatedAtInstant().getEpochSecond(),
					clip.getClip().getCreatedAtInstant().getEpochSecond(),
					clip.getClip().getUrl());

				discordBot.sendServerMessageWithImageUrls(DiscordChannelDecisionMaker.chooseChannel(String.format("%s-clips", channelName)),
					discordMessage,
					false,
					clip.getClip().getThumbnailUrl());
			}
		}
	}
}

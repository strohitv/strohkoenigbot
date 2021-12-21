package tv.strohi.twitch.strohkoenigbot.splatoonapi.stages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonStages;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Component
public class StagesWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatoonStages stages = null;

	private RequestSender stagesLoader;

	@Autowired
	public void setStagesLoader(RequestSender stagesLoader) {
		this.stagesLoader = stagesLoader;
	}

	private TwitchMessageSender channelMessageSender;

	@Autowired
	public void setChannelMessageSender(TwitchMessageSender channelMessageSender) {
		this.channelMessageSender = channelMessageSender;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	// TODO wie mache ich das mit der Benachrichtigung?
	// Überlegung:
	// - Discord: CHECK Ranked-, Turf- und League-Channel. Posten des kompletten Schedules zur vollen Zeit
	// - Twitch: Erinnerung 15 Minuten vorher

	@Scheduled(cron = "20 0 * * * *")
//	@Scheduled(cron = "20 * * * * *")
	public void refreshStages() {
		boolean force = false;

		if (force || stages == null || Arrays.stream(stages.getGachi()).anyMatch(s -> s.getEndTimeAsInstant().isBefore(Instant.now()))) {
			logger.info("checking for new stages");
			stages = stagesLoader.querySplatoonApi("/api/schedules", SplatoonStages.class);

			logger.info("got an answer from api");
			logger.info(stages);
		}

		if (force || Arrays.stream(stages.getGachi()).allMatch(s -> s.getEndTimeAsInstant().isAfter(Instant.now().plus(1, ChronoUnit.HOURS)))) {
			sendDiscordMessageToChannel("turf-war-rotations",
					formatDiscordMessage(stages.getRegular()),
					stages.getRegular()[0].getStage_a().getImage(),
					stages.getRegular()[0].getStage_b().getImage());

			sendDiscordMessageToChannel("ranked-rotations",
					formatDiscordMessage(stages.getGachi()),
					stages.getGachi()[0].getStage_a().getImage(),
					stages.getGachi()[0].getStage_b().getImage());

			sendDiscordMessageToChannel("league-rotations",
					formatDiscordMessage(stages.getLeague()),
					stages.getLeague()[0].getStage_a().getImage(),
					stages.getLeague()[0].getStage_b().getImage());
		}
	}

	private String formatDiscordMessage(SplatoonStages.SplatoonRotation[] rotations) {
		boolean isTurf = rotations[0].getRule().getKey().equals("turf_war");

		StringBuilder builder = new StringBuilder();
		builder.append(String.format("**Current %s rotation**\n", rotations[0].getGame_mode().getName()));

		if (!isTurf) {
			builder.append(String.format("- Mode: %s**%s**\n", getEmoji(rotations[0].getRule().getKey()), rotations[0].getRule().getName()));
		}
		builder.append(String.format("- Stage A: **%s**\n", rotations[0].getStage_a().getName()));
		builder.append(String.format("- Stage B: **%s**\n\n", rotations[0].getStage_b().getName()));

		int hours = 2;
		builder.append("**Next rotations**\n");
		for (int i = 1; i < rotations.length; i++) {
			builder.append(String.format("- in **%d** hours: %s**%s** --- **%s**\n",
					hours,
					!isTurf ? String.format("%s%s --- ", getEmoji(rotations[i].getRule().getKey()), rotations[i].getRule().getName()) : "",
					rotations[i].getStage_a().getName(),
					rotations[i].getStage_b().getName()));
			hours += 2;
		}

		return builder.toString();
	}

	private String getEmoji(String modeId) {
		String emoji;

		switch (modeId) {
			case "splat_zones":
				emoji = "<:sz:922795413734559786> ";
				break;
			case "rainmaker":
				emoji = "<:rm:922795455564378152> ";
				break;
			case "tower_control":
				emoji = "<:tc:922795455753121822> ";
				break;
			case "clam_blitz":
				emoji = "<:cb:922795455623073792> ";
				break;
			default:
				emoji = "";
				break;
		}

		return emoji;
	}

	private void sendDiscordMessageToChannel(String channelName, String message, String firstStageImageUrl, String SecondStageImageUrl) {
		logger.info("Sending out discord notifications to server channel '{}'", channelName);
		discordBot.sendServerMessageWithImages(channelName,
				message,
				String.format("https://app.splatoon2.nintendo.net%s", firstStageImageUrl),
				String.format("https://app.splatoon2.nintendo.net%s", SecondStageImageUrl));
		logger.info("Finished sending out discord notifications to server channel '{}'", channelName);
	}
}

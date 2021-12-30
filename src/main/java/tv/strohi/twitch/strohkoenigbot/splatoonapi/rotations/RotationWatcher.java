package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonRotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonStage;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonRotationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonStageRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStage;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStages;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Component
public class RotationWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatNetStages stages = null;

	private RequestSender stagesLoader;

	private SplatoonStageRepository stageRepository;

	@Autowired
	public void setStageRepository(SplatoonStageRepository stageRepository) {
		this.stageRepository = stageRepository;
	}

	private SplatoonRotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(SplatoonRotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

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

	@Scheduled(cron = "20 0 * * * *")
//	@Scheduled(cron = "20 * * * * *")
	public void sendDiscordNotifications() {
		refreshStages();

		if (Arrays.stream(stages.getGachi()).allMatch(s -> s.getEndTimeAsInstant().isAfter(Instant.now().plus(1, ChronoUnit.HOURS)))) {
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

	@Scheduled(cron = "0 50 * * * *")
//	@Scheduled(cron = "0 * * * * *")
	public void sendStagesToTwitch() {
		refreshStages();

		SplatNetStages.SplatNetRotation turf = Arrays.stream(stages.getRegular())
				.filter(rot -> Instant.now().isBefore(rot.getStartTimeAsInstant())
						&& Instant.now().plus(15, ChronoUnit.MINUTES).isAfter(rot.getStartTimeAsInstant()))
				.findFirst()
				.orElse(null);

		SplatNetStages.SplatNetRotation ranked = Arrays.stream(stages.getGachi())
				.filter(rot -> Instant.now().isBefore(rot.getStartTimeAsInstant())
						&& Instant.now().plus(15, ChronoUnit.MINUTES).isAfter(rot.getStartTimeAsInstant()))
				.findFirst()
				.orElse(null);

		SplatNetStages.SplatNetRotation league = Arrays.stream(stages.getLeague())
				.filter(rot -> Instant.now().isBefore(rot.getStartTimeAsInstant())
						&& Instant.now().plus(15, ChronoUnit.MINUTES).isAfter(rot.getStartTimeAsInstant()))
				.findFirst()
				.orElse(null);

		if (turf != null) {
			channelMessageSender.send("strohkoenig",
					String.format("Next Turf War: %s and %s", turf.getStage_a().getName(), turf.getStage_b().getName()));
		}

		if (ranked != null) {
			channelMessageSender.send("strohkoenig",
					String.format("Next Ranked: %s on %s and %s", ranked.getRule().getName(), ranked.getStage_a().getName(), ranked.getStage_b().getName()));
		}

		if (league != null) {
			channelMessageSender.send("strohkoenig",
					String.format("Next League: %s on %s and %s", league.getRule().getName(), league.getStage_a().getName(), league.getStage_b().getName()));
		}
	}

	private void refreshStages() {
		if (stages == null || Arrays.stream(stages.getGachi()).anyMatch(s -> s.getEndTimeAsInstant().isBefore(Instant.now()))) {
			logger.info("checking for new stages");
			stages = stagesLoader.querySplatoonApi("/api/schedules", SplatNetStages.class);

			saveStagesInDatabase(stages.getRegular());
			saveStagesInDatabase(stages.getGachi());
			saveStagesInDatabase(stages.getLeague());

			logger.info("got an answer from api");
			logger.info(stages);
		}
	}

	private void saveStagesInDatabase(SplatNetStages.SplatNetRotation[] rotations) {
		for (SplatNetStages.SplatNetRotation rotation : rotations) {
			boolean storeRotationIntoDatabase = rotationRepository.findBySplatoonApiIdAndMode(rotation.getId(), SplatoonMode.getModeByName(rotation.getGame_mode().getKey())) == null;

			if (storeRotationIntoDatabase) {
				SplatoonRotation newRotation = new SplatoonRotation();
				newRotation.setSplatoonApiId(rotation.getId());

				newRotation.setStartTime(rotation.getStart_time());
				newRotation.setEndTime(rotation.getEnd_time());

				newRotation.setMode(SplatoonMode.getModeByName(rotation.getGame_mode().getKey()));
				newRotation.setRule(SplatoonRule.getRuleByName(rotation.getRule().getKey()));

				SplatoonStage stageA = ensureStageIsInDatabase(rotation.getStage_a());
				newRotation.setStageAId(stageA.getId());

				SplatoonStage stageB = ensureStageIsInDatabase(rotation.getStage_b());
				newRotation.setStageBId(stageB.getId());

				rotationRepository.save(newRotation);

				discordBot.sendServerMessageWithImages("debug-logs",
						String.format("New **%s** **%s** rotation with id **%d** on **%s** (id %d) and **%s** (id %d) from **%s** to **%s** was stored into Database!",
								newRotation.getMode(),
								newRotation.getRule(),
								newRotation.getId(),
								stageA.getName(),
								stageA.getId(),
								stageB.getName(),
								stageB.getId(),
								newRotation.getStartTimeAsInstant(),
								newRotation.getEndTimeAsInstant()));
			}
		}
	}

	private SplatoonStage ensureStageIsInDatabase(SplatNetStage splatNetStage) {
		SplatoonStage stage = stageRepository.findBySplatoonApiId(splatNetStage.getId());

		if (stage == null) {
			stage = new SplatoonStage();

			stage.setSplatoonApiId(splatNetStage.getId());
			stage.setName(splatNetStage.getName());
			stage.setImage(splatNetStage.getImage());

			stage = stageRepository.save(stage);

			discordBot.sendServerMessageWithImages("debug-logs",
					String.format("New Stage with id **%d** and Name **%s** was stored into Database!",
							stage.getId(),
							stage.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", stage.getImage()));
		}

		return stage;
	}

	private String formatDiscordMessage(SplatNetStages.SplatNetRotation[] rotations) {
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
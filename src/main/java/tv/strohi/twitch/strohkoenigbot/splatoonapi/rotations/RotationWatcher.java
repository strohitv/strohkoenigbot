package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Rotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Stage;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2RotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStages;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Component
public class RotationWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatNetStages stages = null;

	private RequestSender stagesLoader;

	private StagesExporter stagesExporter;

	@Autowired
	public void setStagesExporter(StagesExporter stagesExporter) {
		this.stagesExporter = stagesExporter;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2RotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(Splatoon2RotationRepository rotationRepository) {
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

	@Scheduled(initialDelay = 10000, fixedDelay = Integer.MAX_VALUE)
	public void sendDiscordNotificationsOnLocalDebug() {
		if (DiscordChannelDecisionMaker.isIsLocalDebug()) {
			sendDiscordNotifications();
		}
	}

	@Scheduled(cron = "20 0 * * * *")
//	@Scheduled(cron = "20 * * * * *")
	public void sendDiscordNotifications() {
		refreshStages();

		if (Arrays.stream(stages.getGachi()).allMatch(s -> s.getEndTimeAsInstant().isAfter(Instant.now().plus(1, ChronoUnit.HOURS)))) {
			sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getTurfWarChannel(),
					formatDiscordMessage(stages.getRegular()),
					stages.getRegular()[0].getStage_a().getImage(),
					stages.getRegular()[0].getStage_b().getImage());

			sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getRankedChannel(),
					formatDiscordMessage(stages.getGachi()),
					stages.getGachi()[0].getStage_a().getImage(),
					stages.getGachi()[0].getStage_b().getImage());

			sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getLeagueChannel(),
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

			Account account = accountRepository.findAll().stream()
					.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank() && da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
					.findFirst()
					.orElse(new Account());

			stages = stagesLoader.querySplatoonApiForAccount(account, "/api/schedules", SplatNetStages.class);

			saveStagesInDatabase(stages.getRegular());
			saveStagesInDatabase(stages.getGachi());
			saveStagesInDatabase(stages.getLeague());

			logger.info("finished stage loading");
			logger.debug(stages);
		}
	}

	private void saveStagesInDatabase(SplatNetStages.SplatNetRotation[] rotations) {
		for (SplatNetStages.SplatNetRotation rotation : rotations) {
			boolean storeRotationIntoDatabase = rotationRepository.findBySplatoonApiIdAndMode(rotation.getId(), Splatoon2Mode.getModeByName(rotation.getGame_mode().getKey())) == null;

			if (storeRotationIntoDatabase) {
				Splatoon2Rotation newRotation = new Splatoon2Rotation();
				newRotation.setSplatoonApiId(rotation.getId());

				newRotation.setStartTime(rotation.getStart_time());
				newRotation.setEndTime(rotation.getEnd_time());

				newRotation.setMode(Splatoon2Mode.getModeByName(rotation.getGame_mode().getKey()));
				newRotation.setRule(Splatoon2Rule.getRuleByName(rotation.getRule().getKey()));

				Splatoon2Stage stageA = stagesExporter.loadStage(rotation.getStage_a());
				newRotation.setStageAId(stageA.getId());

				Splatoon2Stage stageB = stagesExporter.loadStage(rotation.getStage_b());
				newRotation.setStageBId(stageB.getId());

				rotationRepository.save(newRotation);

				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
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

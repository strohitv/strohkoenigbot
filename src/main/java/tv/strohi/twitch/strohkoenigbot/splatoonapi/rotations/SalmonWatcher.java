package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetSalmonRunSchedules;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Component
public class SalmonWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatNetSalmonRunSchedules schedules;

	private DiscordAccountRepository discordAccountRepository;

	@Autowired
	public void setDiscordAccountRepository(DiscordAccountRepository discordAccountRepository) {
		this.discordAccountRepository = discordAccountRepository;
	}

	private RequestSender rotationLoader;

	@Autowired
	public void setRotationLoader(RequestSender rotationLoader) {
		this.rotationLoader = rotationLoader;
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

	@Scheduled(cron = "15 0 * * * *")
	public void sendDiscordNotifications() {
		refreshRotations();

		SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail =
				Arrays.stream(schedules.getDetails())
						.filter(s -> Instant.now().minus(10, ChronoUnit.MINUTES).isBefore(s.getStartTimeAsInstant())
								&& Instant.now().plus(5, ChronoUnit.MINUTES).isAfter(s.getStartTimeAsInstant()))
						.findFirst()
						.orElse(null);

		if (detail != null) {
			// new rotation started!
			logger.info("Posting new salmon run rotation into discord");
			sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getSalmonRunChannel(), formatRotation(detail), detail.getStage().getImage());

			logger.info("Posting new salmon run rotation into twitch");
			channelMessageSender.send("strohkoenig",
					String.format("Salmon Run started! Map: %s - Weapons: %s - online for %s hours",
							detail.getStage().getName(),
							formatWeapons(detail),
							(int) Duration.between(detail.getStartTimeAsInstant(), detail.getEndTimeAsInstant()).toHours()));
		}
	}

	private void refreshRotations() {
		if (schedules == null || Arrays.stream(schedules.getDetails()).anyMatch(s -> s.getEndTimeAsInstant().isBefore(Instant.now()))) {
			logger.info("checking for new salmon run rotations");

			Account account = discordAccountRepository.findAll().stream()
					.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank() && da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
					.findFirst()
					.orElse(new Account());

			schedules = rotationLoader.querySplatoonApiForAccount(account, "/api/coop_schedules", SplatNetSalmonRunSchedules.class);

			logger.info("got an answer from api");
			logger.info(schedules);
		}
	}

	private void sendDiscordMessageToChannel(String channelName, String message, String stageImageUrl) {
		logger.info("Sending out discord notifications to server channel '{}'", channelName);
		discordBot.sendServerMessageWithImages(channelName,
				message,
				String.format("https://app.splatoon2.nintendo.net%s", stageImageUrl));
		logger.info("Finished sending out discord notifications to server channel '{}'", channelName);
	}

	private String formatRotation(SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail) {
		StringBuilder builder = new StringBuilder("**Current Salmon Run rotation**\n\n**Stage**:\n- ");
		builder.append(detail.getStage().getName())
				.append("\n\n**Weapons**:\n");

		for (SplatNetSalmonRunSchedules.SplatNetScheduleDetail.WeaponDetail weapon : detail.getWeapons()) {
			if (weapon.getWeapon() != null) {
				builder.append("- ").append(weapon.getWeapon().getName()).append("\n");
			} else if (weapon.getCoop_special_weapon() != null) {
				builder.append("- ").append(weapon.getCoop_special_weapon().getName()).append("\n");
			}
		}

		builder.append("\nRotation will be running for **")
				.append((int) Duration.between(detail.getStartTimeAsInstant(), detail.getEndTimeAsInstant()).toHours())
				.append("** hours!");

		return builder.toString();
	}

	private String formatWeapons(SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail) {
		StringBuilder builder = new StringBuilder();

		for (SplatNetSalmonRunSchedules.SplatNetScheduleDetail.WeaponDetail weapon : detail.getWeapons()) {
			if (weapon.getWeapon() != null) {
				builder.append(weapon.getWeapon().getName()).append(", ");
			} else if (weapon.getCoop_special_weapon() != null) {
				builder.append(weapon.getCoop_special_weapon().getName()).append(", ");
			}
		}

		return builder.substring(0, builder.length() - 2);
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.Splatoon3Stage;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationNotification;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationSlot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3RotationNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationSlotRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.transaction.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Log4j2
public class Splatoon3RotationSenderService {
	private final DiscordBot discordBot;

	private final Splatoon3VsModeDiscordChannelRepository vsModeDiscordChannelRepository;
	private final Splatoon3VsRotationRepository vsRotationRepository;
	private final Splatoon3VsRotationSlotRepository vsRotationSlotRepository;
	private final Splatoon3RotationNotificationRepository notificationRepository;
	private final Splatoon3SrModeDiscordChannelRepository srModeDiscordChannelRepository;
	private final Splatoon3SrRotationRepository srRotationRepository;

	@Transactional
	public void sendRotationsFromDatabase(boolean force) {
		var now = Instant.now();
		var time = getSlotStartTime(now);
		var timeNextDay = getSlotStartTime(now.plus(24, ChronoUnit.HOURS));

		vsModeDiscordChannelRepository.findAll().forEach(channel ->
			vsRotationSlotRepository.findByStartTime(time).stream()
				.filter(slot -> slot.getRotation().getMode().equals(channel.getMode()))
				.filter(slot -> force || Math.abs(slot.getStartTime().getEpochSecond() - Instant.now().getEpochSecond()) <= 300)
				.forEach(slot -> sendVsRotationToDiscord(DiscordChannelDecisionMaker.chooseChannel(channel.getDiscordChannelName()), slot.getRotation())));

		vsRotationSlotRepository.findByStartTime(time).stream()
			.filter(slot -> force || Math.abs(slot.getStartTime().getEpochSecond() - Instant.now().getEpochSecond()) <= 300)
			.forEach(this::sendDiscordNotificationsToUsers);

		vsRotationSlotRepository.findByStartTime(timeNextDay).stream()
			.filter(slot -> force || Math.abs(slot.getStartTime().getEpochSecond() - Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond()) <= 300)
			.forEach(this::sendDiscordNotificationsToUsers);

		srModeDiscordChannelRepository.findAll().forEach(channel ->
			srRotationRepository.findByModeAndStartTimeLessThanEqualAndEndTimeGreaterThan(channel.getMode(), time, time)
				.filter(rotation -> force || Math.abs(rotation.getStartTime().getEpochSecond() - now.getEpochSecond()) <= 300)
				.ifPresent(rotation -> sendSrRotationToDiscord(DiscordChannelDecisionMaker.chooseChannel(channel.getDiscordChannelName()), rotation)));

		log.info("Done posting rotations to discord");
	}

	private void sendVsRotationToDiscord(String channelName, Splatoon3VsRotation rotation) {
		if (rotation.getEventRegulation() == null) {
			sendRegularRotationToDiscord(channelName, rotation);
		} else {
			sendChallengeRotationToDiscord(channelName, rotation);
		}
	}

	private void sendRegularRotationToDiscord(String channelName, Splatoon3VsRotation rotation) {
		String image1 = rotation.getStage1().getImage().getUrl();

		// Tricolor => second stage == null
		String image2 = rotation.getStage2() != null
			? rotation.getStage2().getImage().getUrl()
			: null;

		StringBuilder builder = new StringBuilder("**").append(rotation.getMode().getName()).append("**: ")
			.append("**").append(getEmoji(rotation.getRule().getName())).append(rotation.getRule().getName()).append("**\n")
			.append("- Stage A: **").append(rotation.getStage1().getName()).append("**\n");

		if (rotation.getStage2() != null) {
			builder.append("- Stage B: **").append(rotation.getStage2().getName()).append("**\n");
		}

		builder.append("\n**Next rotations**");

		vsRotationRepository
			.findByModeAndStartTimeAfter(rotation.getMode(), rotation.getStartTime().plus(1, ChronoUnit.MINUTES)).stream()
			.sorted(Comparator.comparing(Splatoon3VsRotation::getStartTime))
			.forEach(r ->
				{
					builder.append("\n- **<t:")
						.append(r.getStartTime().getEpochSecond())
						.append(":t>**")
						.append(" (<t:")
						.append(r.getStartTime().getEpochSecond())
						.append(":R>): ")
						.append(getEmoji(r.getRule().getName()))
						.append(r.getRule().getName())
						.append(" --- **")
						.append(r.getStage1().getName())
						.append("**");

					if (r.getStage2() != null) {
						builder.append(" --- **")
							.append(r.getStage2().getName())
							.append("**");
					}
				}
			);

		if (image2 != null) {
			discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1, image2);
		} else {
			discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1);
		}
	}

	private void sendChallengeRotationToDiscord(String channelName, Splatoon3VsRotation rotation) {
		var event = rotation.getEventRegulation();

		String image1 = rotation.getStage1().getImage().getUrl();
		String image2 = rotation.getStage2().getImage().getUrl();

		StringBuilder builder = new StringBuilder("**").append(rotation.getMode().getName()).append("**:\n")
			.append("- Event: **").append(event.getName()).append("**\n")
			.append("- Description: **").append(event.getDescription()).append("**\n")
			.append("- Rules:\n```\n").append(event.getRegulation().replace("<br />", "\n")).append("\n```\n")
			.append("**Rotation details**\n- Game Rule: **")
			.append(getEmoji(rotation.getRule().getName()))
			.append(rotation.getRule().getName())
			.append("**\n")
			.append("- Stage A: **").append(rotation.getStage1().getName()).append("**\n")
			.append("- Stage B: **").append(rotation.getStage2().getName()).append("**\n\n");

		var futureSlots = rotation.getSlots().stream()
			.filter(t -> t.getStartTime().isAfter(Instant.now()))
			.sorted(Comparator.comparing(Splatoon3VsRotationSlot::getStartTime))
			.collect(Collectors.toList());
		if (!futureSlots.isEmpty()) {
			builder.append("**Future Slots**");

			futureSlots.forEach(fs -> builder.append("\n- **<t:")
				.append(fs.getStartTime().getEpochSecond())
				.append(":t>**")
				.append(" (<t:")
				.append(fs.getStartTime().getEpochSecond())
				.append(":R>)")
			);

			builder.append("\n\n");
		}

		builder.append("**Next challenges**");

		vsRotationRepository
			.findByModeAndStartTimeAfter(rotation.getMode(), rotation.getStartTime().plus(1, ChronoUnit.MINUTES)).stream()
			.sorted(Comparator.comparing(Splatoon3VsRotation::getStartTime))
			.forEach(r ->
				builder.append("\n- **<t:")
					.append(r.getStartTime().getEpochSecond())
					.append(":f>** (<t:")
					.append(r.getStartTime().getEpochSecond())
					.append(":R>) --- **")
					.append(r.getEventRegulation().getName())
					.append("** --- ")
					.append(getEmoji(r.getRule().getName()))
					.append(r.getRule().getName())
					.append(" --- **")
					.append(r.getStage1().getName())
					.append("** --- **")
					.append(r.getStage2().getName())
					.append("**")
			);

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1, image2);
	}

	private void sendSrRotationToDiscord(String channelName, Splatoon3SrRotation rotation) {
		StringBuilder builder = new StringBuilder(String.format("**%s**:\n\n**Stage**:\n- ", rotation.getMode().getName()))
			.append(rotation.getStage().getName());

		if (rotation.getBoss() != null) {
			builder.append("\n\n**Boss**: \n- ").append(rotation.getBoss().getName());
		}

		builder.append("\n\n**Weapons**:\n");

		Stream.of(rotation.getWeapon1(), rotation.getWeapon2(), rotation.getWeapon3(), rotation.getWeapon4())
			.forEach(w -> builder.append("- ").append(w.getName()).append("\n"));

		builder.append("\nRotation will be running until **<t:")
			.append(rotation.getEndTime().getEpochSecond())
			.append(":f>** (<t:")
			.append(rotation.getEndTime().getEpochSecond())
			.append(":R>)");

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), rotation.getStage().getImage().getUrl());
	}

	public String getEmoji(String modeId) {
		String emoji;

		switch (modeId) {
			case "Splat Zones":
				emoji = "<:zones:1047644886368796673> ";
				break;
			case "Rainmaker":
				emoji = "<:rainmaker:1047644903326359702> ";
				break;
			case "Tower Control":
				emoji = "<:tower:1047644913967300749> ";
				break;
			case "Clam Blitz":
				emoji = "<:clams:1047644923710677072> ";
				break;
			default:
				emoji = "";
				break;
		}

		return emoji;
	}

	private Instant getSlotStartTime(Instant base) {
		return base.atZone(ZoneOffset.UTC)
			.truncatedTo(ChronoUnit.DAYS)
			.withHour(base.atZone(ZoneOffset.UTC).getHour() / 2 * 2)
			.withMinute(0)
			.withSecond(0)
			.withNano(0)
			.toInstant();
	}

	private void sendDiscordNotificationsToUsers(Splatoon3VsRotationSlot rotationSlot) {
		var mode = ModeFilter.getFromSplatNetApiName(rotationSlot.getRotation().getMode().getName());
		var rule = RuleFilter.getFromSplatNetApiName(rotationSlot.getRotation().getRule().getName());
		var rotationNotifications = notificationRepository.findByModeAndRule(mode, rule);

		for (var notification : rotationNotifications) {
			// excluded stages
			var excludedStages = Splatoon3Stage.resolveFromNumber(notification.getExcludedStages());
			if (excludedStages.length > 0 && Arrays.stream(excludedStages).anyMatch(es -> es.getName().equals(rotationSlot.getRotation().getStage1().getName()) || (rotationSlot.getRotation().getStage2() != null && es.getName().equals(rotationSlot.getRotation().getStage2().getName())))) {
				continue;
			}

			// included stages
			var includedStages = Splatoon3Stage.resolveFromNumber(notification.getIncludedStages());
			if (includedStages.length > 0 && Arrays.stream(includedStages).noneMatch(es -> es.getName().equals(rotationSlot.getRotation().getStage1().getName()) || (rotationSlot.getRotation().getStage2() != null && es.getName().equals(rotationSlot.getRotation().getStage2().getName())))) {
				continue;
			}

			// times
			if (notification.getZoneId() != null && !notification.getZoneId().isBlank()) {
				// can check timezone
				var startTime = rotationSlot.getStartTime().atZone(ZoneId.of(notification.getZoneId()));

				if (isOutSideAllowedTime(notification, startTime)) {
					continue;
				}
			}

			var stages = Stream.of(rotationSlot.getRotation().getStage1(), rotationSlot.getRotation().getStage2())
				.filter(Objects::nonNull)
				.map(rs -> rs.getImage().getUrl())
				.toArray(String[]::new);

			sendDiscordMessageToUser(notification.getAccount().getDiscordId(),
				formatDiscordMessage(rotationSlot, mode.getName(), notification.getZoneId()),
				stages);
		}
	}

	private String formatDiscordMessage(Splatoon3VsRotationSlot rotationSlot, String gameMode, String timezone) {
		var isTurf = rotationSlot.getRotation().getRule().getApiRule().equalsIgnoreCase("turf_war");

		var builder = new StringBuilder();

		var hadTimeZone = timezone != null;
		if (!hadTimeZone) {
			timezone = "Europe/Berlin";
		}

		if (Instant.now().atZone(ZoneId.of(timezone)).plusHours(4).isAfter(rotationSlot.getStartTime().atZone(ZoneId.of(timezone)))) {
			builder.append(String.format("## Current __%s__ rotation\n", gameMode));
		} else {
			builder.append(String.format("## New __%s__ rotation will start in __%d__ hours",
				gameMode,
				Duration.between(Instant.now(), rotationSlot.getStartTime()).abs().toHours() + 1));

			builder.append(String.format("\nIt will start at **<t:%d:F>** (<t:%d:R>)", rotationSlot.getStartTime().getEpochSecond(), rotationSlot.getStartTime().getEpochSecond()));

			builder.append("\n");
		}

		if (!isTurf) {
			builder.append(String.format("- Rule: %s**%s**\n", getEmoji(rotationSlot.getRotation().getRule().getName()), rotationSlot.getRotation().getRule().getName()));
		}

		if (rotationSlot.getRotation().getEventRegulation() != null) {
			builder.append("- Event: ").append(rotationSlot.getRotation().getEventRegulation().getName()).append("\n");
			builder.append("```\n").append(rotationSlot.getRotation().getEventRegulation().getRegulation().replace("<br />", "\n")).append("\n```\n");
		}

		builder.append(String.format("- Stage 1: **%s**\n", rotationSlot.getRotation().getStage1().getName()));

		if (rotationSlot.getRotation().getStage2() != null) {
			builder.append(String.format("- Stage 2: **%s**\n\n", rotationSlot.getRotation().getStage2().getName()));
		}

		return builder.toString();
	}

	private void sendDiscordMessageToUser(long discordId, String message, String... imageUrls) {
		log.info("Sending out discord notifications to server channel '{}'", discordId);
		discordBot.sendPrivateMessageWithImageUrls(discordId, message, imageUrls);
		log.info("Finished sending out discord notifications to server channel '{}'", discordId);
	}

	private boolean isOutSideAllowedTime(Splatoon3VsRotationNotification notification, ZonedDateTime startTime) {
		var allowedStartHour = 0;
		var allowedEndHour = 23;

		switch (startTime.getDayOfWeek()) {
			case MONDAY:
				if (!notification.isNotifyMonday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeMonday();
				allowedEndHour = notification.getEndTimeMonday();
				break;
			case TUESDAY:
				if (!notification.isNotifyTuesday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeTuesday();
				allowedEndHour = notification.getEndTimeTuesday();
				break;
			case WEDNESDAY:
				if (!notification.isNotifyWednesday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeWednesday();
				allowedEndHour = notification.getEndTimeWednesday();
				break;
			case THURSDAY:
				if (!notification.isNotifyThursday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeThursday();
				allowedEndHour = notification.getEndTimeThursday();
				break;
			case FRIDAY:
				if (!notification.isNotifyFriday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeFriday();
				allowedEndHour = notification.getEndTimeFriday();
				break;
			case SATURDAY:
				if (!notification.isNotifySaturday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeSaturday();
				allowedEndHour = notification.getEndTimeSaturday();
				break;
			case SUNDAY:
				if (!notification.isNotifySunday()) {
					return true;
				}
				allowedStartHour = notification.getStartTimeSunday();
				allowedEndHour = notification.getEndTimeSunday();
				break;
		}

		return startTime.getHour() < allowedStartHour || startTime.getHour() > allowedEndHour;
	}
}

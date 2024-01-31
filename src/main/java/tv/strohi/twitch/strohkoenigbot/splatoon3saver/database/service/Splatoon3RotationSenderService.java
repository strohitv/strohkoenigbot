package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationSlot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationSlotRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class Splatoon3RotationSenderService {
	private final DiscordBot discordBot;

	private final Splatoon3VsModeDiscordChannelRepository vsModeDiscordChannelRepository;
	private final Splatoon3VsRotationRepository vsRotationRepository;
	private final Splatoon3VsRotationSlotRepository vsRotationSlotRepository;
	private final Splatoon3SrModeDiscordChannelRepository srModeDiscordChannelRepository;
	private final Splatoon3SrRotationRepository srRotationRepository;

	@Transactional
	public void sendRotationsFromDatabase(boolean force) {
		var time = getSlotStartTime(Instant.now());

		vsModeDiscordChannelRepository.findAll().forEach(channel ->
			vsRotationSlotRepository.findByStartTime(time).stream()
				.filter(slot -> slot.getRotation().getMode().equals(channel.getMode()))
				.filter(slot -> force || Math.abs(slot.getStartTime().getEpochSecond() - Instant.now().getEpochSecond()) <= 300)
				.forEach(slot -> sendVsRotationToDiscord(DiscordChannelDecisionMaker.chooseChannel(channel.getDiscordChannelName()), slot.getRotation())));

		srModeDiscordChannelRepository.findAll().forEach(channel ->
			srRotationRepository.findByModeAndStartTimeBeforeAndEndTimeAfter(channel.getMode(), time, time)
				.filter(rotation -> force || Math.abs(rotation.getStartTime().getEpochSecond() - Instant.now().getEpochSecond()) <= 300)
				.ifPresent(rotation -> sendSrRotationToDiscord(DiscordChannelDecisionMaker.chooseChannel(channel.getDiscordChannelName()), rotation)));
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
		String image2 = rotation.getStage2().getImage().getUrl();

		StringBuilder builder = new StringBuilder("**").append(rotation.getMode().getName()).append("**: ")
			.append("**").append(getEmoji(rotation.getRule().getName())).append(rotation.getRule().getName()).append("**\n")
			.append("- Stage A: **").append(rotation.getStage1().getName()).append("**\n")
			.append("- Stage B: **").append(rotation.getStage2().getName()).append("**\n\n")
			.append("**Next rotations**");

		vsRotationRepository
			.findByModeAndStartTimeAfter(rotation.getMode(), rotation.getStartTime().plus(1, ChronoUnit.MINUTES)).stream()
			.sorted(Comparator.comparing(Splatoon3VsRotation::getStartTime))
			.forEach(r ->
				builder.append("\n- **<t:")
					.append(r.getStartTime().getEpochSecond())
					.append(":R>**: ")
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
			.append("- Stage B: **").append(rotation.getStage1().getName()).append("**\n\n");

		var futureSlots = rotation.getSlots().stream()
			.filter(t -> t.getStartTime().isAfter(Instant.now()))
			.sorted(Comparator.comparing(Splatoon3VsRotationSlot::getStartTime))
			.collect(Collectors.toList());
		if (futureSlots.size() > 0) {
			builder.append("**Future Slots**");

			futureSlots.forEach(fs -> builder.append("\n- <t:")
				.append(fs.getStartTime().getEpochSecond()).append(":R>")
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

	private String getEmoji(String modeId) {
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
}

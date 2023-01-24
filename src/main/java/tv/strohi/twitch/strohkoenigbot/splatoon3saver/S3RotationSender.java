package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.RotationSchedulesResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopGroupingSchedule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Rotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.RotationMatchSetting;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3RotationSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;
	private final S3ApiQuerySender requestSender;
	private final DiscordBot discordBot;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3RotationSender_schedule", CronSchedule.getScheduleString("30 0 * * * *"), this::refreshRotations);
	}

	private void refreshRotations() {
		refreshRotations(false);
	}

	public void refreshRotations(boolean force) {
		Account account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to refresh rotations!");
			return;
		}

		logger.info("Start posting rotations to discord");
		try {
			String allRotationsResponse = requestSender.queryS3Api(account, S3RequestKey.RotationSchedules.getKey());
			RotationSchedulesResult rotationSchedulesResult = new ObjectMapper().readValue(allRotationsResponse, RotationSchedulesResult.class);

			sendRotations(List.of(
					rotationSchedulesResult.getData().getRegularSchedules(),
					rotationSchedulesResult.getData().getBankaraSchedules(),
					rotationSchedulesResult.getData().getXSchedules(),
					rotationSchedulesResult.getData().getLeagueSchedules(),
					rotationSchedulesResult.getData().getFestSchedules()
			), force);

			sendSalmonRotations(rotationSchedulesResult.getData().getCoopGroupingSchedule(), force);
		} catch (JsonProcessingException e) {
			logSender.sendLogs(logger, String.format("exception during rotation refresh!! %s", e.getMessage()));
			logger.error(e);
		}

		logger.info("Done posting rotations to discord");
	}

	private void sendSalmonRotations(CoopGroupingSchedule coopGroupingSchedule, boolean force) {
		String regularChannelName = DiscordChannelDecisionMaker.getS3SalmonRunChannel();
		String bigRunChannelName = DiscordChannelDecisionMaker.getS3SalmonRunBigRunChannel();

		CoopRotation regularRotation = Arrays.stream(coopGroupingSchedule.getRegularSchedules().getNodes())
				.min(Comparator.comparing(CoopRotation::getStartTimeAsInstant))
				.orElse(null);

		CoopRotation bigRunRotation = Arrays.stream(coopGroupingSchedule.getBigRunSchedules().getNodes())
				.min(Comparator.comparing(CoopRotation::getStartTimeAsInstant))
				.orElse(null);

		if (regularRotation != null) {
			sendSalmonRotationToDiscord(regularChannelName, "Salmon Run", regularRotation, force);
		}

		if (bigRunRotation != null) {
			sendSalmonRotationToDiscord(bigRunChannelName, "Big Run", bigRunRotation, force);
		}
	}

	private void sendSalmonRotationToDiscord(String channelName, String typeName, CoopRotation rotation, boolean force) {
		if ((force && rotation.getStartTimeAsInstant().isBefore(Instant.now()))
				|| (rotation.getStartTimeAsInstant().isBefore(Instant.now()) && rotation.getStartTimeAsInstant().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES)))
		) {
			StringBuilder builder = new StringBuilder(String.format("**Current %s rotation**\n\n**Stage**:\n- ", typeName))
					.append(rotation.getSetting().getCoopStage().getName())
					.append("\n\n**Weapons**:\n");

			Arrays.stream(rotation.getSetting().getWeapons()).forEach(w ->
					builder.append("- ").append(w.getName()).append("\n"));

			builder.append("\nRotation will be running for **")
					.append((int) Duration.between(rotation.getStartTimeAsInstant(), rotation.getEndTimeAsInstant()).toHours())
					.append("** hours!");

			discordBot.sendServerMessageWithImages(channelName, builder.toString(), rotation.getSetting().getCoopStage().getImage().getUrl());
		}
	}

	private void sendRotations(List<RotationSchedulesResult.Node> rotationSchedulesResult, boolean force) {
		for (var schedule : rotationSchedulesResult) {
			Rotation currentRotation = schedule.getNodes()[0];
			if (force || (
					currentRotation.getStartTimeAsInstant().isBefore(Instant.now())
							&& currentRotation.getStartTimeAsInstant().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES)))
			) {
				// new rotation -> send notifications
				List<Rotation> rotations = Arrays.stream(schedule.getNodes()).collect(Collectors.toList());

				if (currentRotation.getBankaraMatchSettings() != null) {
					// WONDERFUL, special case thanks to different class design here...
					String seriesChannelName = DiscordChannelDecisionMaker.getS3AnarchySeriesChannel();
					String openChannelName = DiscordChannelDecisionMaker.getS3AnarchyOpenChannel();

					List<RotationMatchSettingWithTime> seriesRotations = getRotationSettingsWithTimes(rotations, "CHALLENGE");
					List<RotationMatchSettingWithTime> openRotations = getRotationSettingsWithTimes(rotations, "OPEN");

					sendRotationToDiscord(seriesChannelName, seriesRotations);
					sendRotationToDiscord(openChannelName, openRotations);
				} else {
					String channelName = decideChannelToPostIn(currentRotation);

					if (channelName != null) {
						List<RotationMatchSettingWithTime> rotationMatchSettingsWithTimes = getRotationSettingsWithTimes(rotations);

						sendRotationToDiscord(channelName, rotationMatchSettingsWithTimes);
					}
				}
			}
		}
	}

	private void sendRotationToDiscord(String channelName, List<RotationMatchSettingWithTime> rotations) {
		RotationMatchSettingWithTime firstRotation = rotations.stream().min(Comparator.comparing(RotationMatchSettingWithTime::getStartTime)).orElseThrow();

		if (firstRotation.getRotationMatchSetting() == null) {
			// Splatfest whenever a fest is not active.
			return;
		}

		String image1 = firstRotation.getRotationMatchSetting().getVsStages()[0].getImage().getUrl();
		String image2 = firstRotation.getRotationMatchSetting().getVsStages()[1].getImage().getUrl();

		StringBuilder builder = new StringBuilder("**Current rotation**\n")
				.append("- Rule: **").append(getEmoji(firstRotation.getRotationMatchSetting().getVsRule().getName())).append(firstRotation.getRotationMatchSetting().getVsRule().getName()).append("**\n")
				.append("- Stage A: **").append(firstRotation.getRotationMatchSetting().getVsStages()[0].getName()).append("**\n")
				.append("- Stage B: **").append(firstRotation.getRotationMatchSetting().getVsStages()[1].getName()).append("**\n\n")
				.append("**Next rotations**");

		rotations.stream()
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(RotationMatchSettingWithTime::getStartTime))
				.filter(r -> r.getRotationMatchSetting() != null)
				.skip(1)
				.forEach(r ->
						builder.append("\n- in **")
								.append(getHourDifference(Instant.now(), r.getStartTime()))
								.append("** hours: ")
								.append(getEmoji(r.getRotationMatchSetting().getVsRule().getName()))
								.append(r.getRotationMatchSetting().getVsRule().getName())
								.append(" --- **")
								.append(r.getRotationMatchSetting().getVsStages()[0].getName())
								.append("** --- **")
								.append(r.getRotationMatchSetting().getVsStages()[1].getName())
								.append("**")
				);

		discordBot.sendServerMessageWithImages(channelName, builder.toString(), image1, image2);
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

	private int getHourDifference(Instant now, Instant startTime) {
		return Duration.between(now, startTime).toHoursPart() + 1;
	}

	private List<RotationMatchSettingWithTime> getRotationSettingsWithTimes(List<Rotation> rotationSchedulesResult) {
		return getRotationSettingsWithTimes(rotationSchedulesResult, null);
	}

	private List<RotationMatchSettingWithTime> getRotationSettingsWithTimes(List<Rotation> rotationSchedulesResult, String anarchyMode) {
		var list = new ArrayList<RotationMatchSettingWithTime>();

		if (anarchyMode != null) {
			list.addAll(rotationSchedulesResult.stream()
					.map(rsr -> new RotationMatchSettingWithTime(rsr.getStartTimeAsInstant(),
							Arrays.stream(rsr.getBankaraMatchSettings())
									.filter(bms -> anarchyMode.equals(bms.getMode()))
									.findFirst()
									.orElse(null)))
					.collect(Collectors.toList()));
		} else {
			list.addAll(rotationSchedulesResult.stream()
					.map(rsr -> new RotationMatchSettingWithTime(rsr.getStartTimeAsInstant(), getRotation(rsr)))
					.collect(Collectors.toList()));
		}

		return list;
	}

	private RotationMatchSetting getRotation(Rotation rsr) {
		RotationMatchSetting result;

		if (rsr.getRegularMatchSetting() != null) {
			result = rsr.getRegularMatchSetting();
		} else if (rsr.getXMatchSetting() != null) {
			result = rsr.getXMatchSetting();
		} else if (rsr.getLeagueMatchSetting() != null) {
			result = rsr.getLeagueMatchSetting();
		} else { //  if (rsr.getFestMatchSetting() != null) {
			result = rsr.getFestMatchSetting();
		}

		return result;
	}

	private String decideChannelToPostIn(Rotation currentRotation) {
		String channelName = DiscordChannelDecisionMaker.getDebugChannelName();

		if (currentRotation.getRegularMatchSetting() != null) {
			channelName = DiscordChannelDecisionMaker.getS3TurfWarChannel();
		} else if (currentRotation.getXMatchSetting() != null) {
			channelName = DiscordChannelDecisionMaker.getS3XRankChannel();
		} else if (currentRotation.getLeagueMatchSetting() != null) {
			channelName = DiscordChannelDecisionMaker.getS3LeagueChannel();
		} else if (currentRotation.getFestMatchSetting() != null && currentRotation.getFestMatchSetting().getVsStages() == null) {
			// other game mode DURING splatfest
			channelName = null;
		} else if (currentRotation.getFestMatchSetting() != null) {
			// real splatfest game mode
			channelName = DiscordChannelDecisionMaker.getS3SplatfestChannel();
		}

		return channelName;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	private static class RotationMatchSettingWithTime {
		private Instant startTime;
		private RotationMatchSetting rotationMatchSetting;
	}
}

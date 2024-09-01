package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SplatoonStage;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2RotationNotification;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Rotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Stage;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.Splatoon2RotationNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2RotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStages;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

	private Splatoon2RotationNotificationRepository notificationRepository;

	@Autowired
	public void setNotificationRepository(Splatoon2RotationNotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
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

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	private ExceptionLogger exceptionLogger;

	@Autowired
	public void setExceptionLogger(ExceptionLogger exceptionLogger) {
		this.exceptionLogger = exceptionLogger;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("RotationWatcher_schedule", CronSchedule.getScheduleString("20 0 * * * *"), this::sendDiscordNotifications);
		schedulingService.registerOnce("RotationWatcher_sendDiscordNotificationsOnLocalDebug", 2, this::sendDiscordNotificationsOnLocalDebug);

//		try {
//			sendDiscordNotificationsOnLocalDebug();
//		} catch (Exception ignored) {
//		}
	}

	//	@Scheduled(initialDelay = 10000, fixedDelay = Integer.MAX_VALUE)
	public void sendDiscordNotificationsOnLocalDebug() {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			sendDiscordNotifications();
		}
	}

	// ON LOCAL DEBUG: Remember lines 119, 125, 131!!
//	@Scheduled(cron = "20 0 * * * *")
//	@Scheduled(cron = "20 * * * * *")
	public void sendDiscordNotifications() {
		refreshStages();

		if (stages != null) {
			if (Arrays.stream(stages.getGachi()).allMatch(s -> s.getEndTimeAsInstant().isAfter(Instant.now().plus(1, ChronoUnit.HOURS)))) {
				sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getS2TurfWarChannel(),
						formatDiscordMessage(stages.getRegular()),
						stages.getRegular()[0].getStage_a().getImage(),
						stages.getRegular()[0].getStage_b().getImage());

				sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getS2RankedChannel(),
						formatDiscordMessage(stages.getGachi()),
						stages.getGachi()[0].getStage_a().getImage(),
						stages.getGachi()[0].getStage_b().getImage());

				sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getS2LeagueChannel(),
						formatDiscordMessage(stages.getLeague()),
						stages.getLeague()[0].getStage_a().getImage(),
						stages.getLeague()[0].getStage_b().getImage());

				if (stages.getRegular().length > 0 && !DiscordChannelDecisionMaker.isLocalDebug()) {
//			if (stages.getRegular().length > 0) {
					sendDiscordNotificationsToUsers(stages.getRegular()[0]);
					sendDiscordNotificationsToUsers(stages.getRegular()[stages.getRegular().length - 1]);
				}

				if (stages.getGachi().length > 0 && !DiscordChannelDecisionMaker.isLocalDebug()) {
//			if (stages.getGachi().length > 0) {
					sendDiscordNotificationsToUsers(stages.getGachi()[0]);
					sendDiscordNotificationsToUsers(stages.getGachi()[stages.getGachi().length - 1]);
				}

				if (stages.getLeague().length > 0 && !DiscordChannelDecisionMaker.isLocalDebug()) {
//			if (stages.getLeague().length > 0) {
					sendDiscordNotificationsToUsers(stages.getLeague()[0]);
					sendDiscordNotificationsToUsers(stages.getLeague()[stages.getLeague().length - 1]);
				}
			}
		} else {
			logger.error("stages were null!");
		}
	}

	//	@Scheduled(cron = "0 50 * * * *")
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
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(new Account());

			stages = stagesLoader.querySplatoonApiForAccount(account, "/api/schedules", SplatNetStages.class);

			if (stages != null) {
				saveStagesInDatabase(stages.getRegular());
				saveStagesInDatabase(stages.getGachi());
				saveStagesInDatabase(stages.getLeague());

				logger.info("finished stage loading");
				logger.debug(stages);
			} else {
				logger.error("stages were null!");
			}
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

				try {
					rotationRepository.save(newRotation);
				} catch (Exception ex) {
					exceptionLogger.logException(logger, ex);
				}

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

	private void sendDiscordNotificationsToUsers(SplatNetStages.SplatNetRotation rotation) {
		ModeFilter mode = ModeFilter.getFromSplatNetApiName(rotation.getGame_mode().getName());
		RuleFilter rule = RuleFilter.getFromSplatNetApiName(rotation.getRule().getName());
		List<Splatoon2RotationNotification> rotationNotifications = notificationRepository.findByModeAndRule(mode, rule);

		List<Account> accountsToNotify = new ArrayList<>();

		for (Splatoon2RotationNotification notification : rotationNotifications) {
			if (accountsToNotify.contains(notification.getAccount())) {
				continue;
			}

			// excluded stages
			SplatoonStage[] excludedStages = SplatoonStage.resolveFromNumber(notification.getExcludedStages());
			if (excludedStages.length > 0 && Arrays.stream(excludedStages).anyMatch(es -> es.getName().equals(rotation.getStage_a().getName()) || es.getName().equals(rotation.getStage_b().getName()))) {
				continue;
			}

			// included stages
			SplatoonStage[] includedStages = SplatoonStage.resolveFromNumber(notification.getIncludedStages());
			if (includedStages.length > 0 && Arrays.stream(includedStages).noneMatch(es -> es.getName().equals(rotation.getStage_a().getName()) || es.getName().equals(rotation.getStage_b().getName()))) {
				continue;
			}

			// times
			if (notification.getAccount().getTimezone() != null && !notification.getAccount().getTimezone().isBlank()) {
				// can check timezone
				ZonedDateTime startTime = rotation.getStartTimeAsInstant().atZone(ZoneId.of(notification.getAccount().getTimezone()));

				if (isOutSideAllowedTime(notification, startTime)) {
					continue;
				}
			}

			accountsToNotify.add(notification.getAccount());
		}

		for (Account recipient : accountsToNotify) {
			sendDiscordMessageToUser(recipient.getDiscordId(),
					formatDiscordMessage(rotation, recipient.getTimezone()),
					rotation.getStage_a().getImage(),
					rotation.getStage_b().getImage());
		}
	}

	private String formatDiscordMessage(SplatNetStages.SplatNetRotation[] rotations) {
		boolean isTurf = rotations[0].getRule().getKey().equals("turf_war");

		StringBuilder builder = new StringBuilder();
		builder.append(String.format("**Current %s rotation**\n", rotations[0].getGame_mode().getName()));

		if (!isTurf) {
			builder.append(String.format("- Rule: %s**%s**\n", getEmoji(rotations[0].getRule().getKey()), rotations[0].getRule().getName()));
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

	private String formatDiscordMessage(SplatNetStages.SplatNetRotation rotation, String timezone) {
		boolean isTurf = rotation.getRule().getKey().equals("turf_war");

		StringBuilder builder = new StringBuilder();

		boolean hadTimeZone = timezone != null;
		if (!hadTimeZone) {
			timezone = "Europe/Berlin";
		}

		if (Instant.now().atZone(ZoneId.of(timezone)).plus(4, ChronoUnit.HOURS).isAfter(rotation.getStartTimeAsInstant().atZone(ZoneId.of(timezone)))) {
			builder.append(String.format("Current **%s** rotation\n", rotation.getGame_mode().getName()));
		} else {
			builder.append(String.format("New **%s** rotation will start in **%d** hours",
					rotation.getGame_mode().getName(),
					Duration.between(Instant.now(), rotation.getStartTimeAsInstant()).abs().toHours() + 1));

			builder.append(String.format("\nIt will start at **<t:%d:F>**", rotation.getStart_time()));

			builder.append("\n");
		}

		if (!isTurf) {
//			builder.append(String.format("- Mode: **%s**\n", rotation.getGame_mode().getName()));
			builder.append(String.format("- Rule: %s**%s**\n", getEmoji(rotation.getRule().getKey()), rotation.getRule().getName()));
		}
		builder.append(String.format("- Stage A: **%s**\n", rotation.getStage_a().getName()));
		builder.append(String.format("- Stage B: **%s**\n\n", rotation.getStage_b().getName()));

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
		discordBot.sendServerMessageWithImageUrls(channelName,
				message,
				String.format("https://app.splatoon2.nintendo.net%s", firstStageImageUrl),
				String.format("https://app.splatoon2.nintendo.net%s", SecondStageImageUrl));
		logger.info("Finished sending out discord notifications to server channel '{}'", channelName);
	}

	private void sendDiscordMessageToUser(long discordId, String message, String firstStageImageUrl, String SecondStageImageUrl) {
		logger.info("Sending out discord notifications to server channel '{}'", discordId);
		discordBot.sendPrivateMessageWithImageUrls(discordId,
				message,
				String.format("https://app.splatoon2.nintendo.net%s", firstStageImageUrl),
				String.format("https://app.splatoon2.nintendo.net%s", SecondStageImageUrl));
		logger.info("Finished sending out discord notifications to server channel '{}'", discordId);
	}

	private boolean isOutSideAllowedTime(Splatoon2RotationNotification notification, ZonedDateTime startTime) {
		int allowedStartHour = 0;
		int allowedEndHour = 23;

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

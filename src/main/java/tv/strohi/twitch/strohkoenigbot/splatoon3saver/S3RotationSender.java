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
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationSlot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeDiscordChannelRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationSlotRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3SrRotationService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3VsRotationService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.RotationSchedulesResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopGroupingSchedule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Rotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.RotationMatchSetting;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Transactional
@RequiredArgsConstructor
public class S3RotationSender {
	private final ObjectMapper mapper = new ObjectMapper();

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	private final Splatoon3VsModeDiscordChannelRepository vsModeDiscordChannelRepository;
	private final Splatoon3VsRotationRepository vsRotationRepository;
	private final Splatoon3VsRotationSlotRepository vsRotationSlotRepository;
	private final Splatoon3SrModeDiscordChannelRepository srModeDiscordChannelRepository;
	private final Splatoon3SrRotationRepository srRotationRepository;

	private final S3ApiQuerySender requestSender;
	private final DiscordBot discordBot;
	private final ExceptionLogger exceptionLogger;

	private SchedulingService schedulingService;

	private final Splatoon3VsRotationService vsRotationService;
	private final Splatoon3SrRotationService srRotationService;

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
		var useNewWay = configurationRepository.findByConfigName("s3UseDatabase").stream()
			.map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
			.findFirst()
			.orElse(false);

		if (useNewWay) {
			importRotationsToDatabase();
			sendRotationsFromDatabase(force);
		} else {
			refreshRotationsOld(force);
		}
	}

	public void importRotationsToDatabase() {
		Account account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to import rotations to database!");
			return;
		}

		logger.info("Start importing rotations to database");
		try {
			importSrRotationsFromGameResultsFolder(account);

			String allRotationsResponse = requestSender.queryS3Api(account, S3RequestKey.RotationSchedules.getKey());
			RotationSchedulesResult rotationSchedulesResult = new ObjectMapper().readValue(allRotationsResponse, RotationSchedulesResult.class);

			Stream
				.of(rotationSchedulesResult.getData().getRegularSchedules(),
					rotationSchedulesResult.getData().getBankaraSchedules(),
					rotationSchedulesResult.getData().getXSchedules(),
					rotationSchedulesResult.getData().getFestSchedules())
				.flatMap(r -> Arrays.stream(r.getNodes()))
				.forEach(vsRotationService::ensureRotationExists);

			Arrays.stream(rotationSchedulesResult.getData().getEventSchedules().getNodes())
				.forEach(vsRotationService::ensureRotationExists);

			Stream
				.of(rotationSchedulesResult.getData().getCoopGroupingSchedule().getRegularSchedules(),
					rotationSchedulesResult.getData().getCoopGroupingSchedule().getBigRunSchedules(),
					rotationSchedulesResult.getData().getCoopGroupingSchedule().getTeamContestSchedules())
				.flatMap(r -> Arrays.stream(r.getNodes()))
				.forEach(srRotationService::ensureRotationExists);
		} catch (JsonProcessingException e) {
//			logSender.sendLogs(logger, String.format("exception during rotation refresh!! %s", e.getMessage()));
			logger.error(e);
			logSender.sendLogs(logger, "An exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		} catch (IOException e) {
			logger.error(e);
			logSender.sendLogs(logger, "An IO exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		}

		logger.info("Done posting rotations to discord");
	}

	public void importSrRotationsFromGameResultsFolder(Account account) throws IOException {
		var accountUUIDHash = String.format("%05d", account.getId());
		importSrRotationsFromGameResultsFolder(accountUUIDHash);
	}

	public void importSrRotationsFromGameResultsFolder(String accountUUIDHash) throws IOException {
		importSrRotationsFromGameResultsFolder(accountUUIDHash, false);
	}

	public void importSrRotationsFromGameResultsFolder(String accountUUIDHash, boolean shouldDelete) throws IOException {
		var folderName = configurationRepository.findByConfigName("gameResultsFolder").stream()
			.map(Configuration::getConfigValue)
			.findFirst()
			.orElse(accountUUIDHash);

		Path directory = Path.of("game-results", folderName);
		if (Files.exists(directory)) {
			var allFiles = Files.walk(directory)
				.filter(f -> f.getFileName().toString().startsWith("Salmon_List_"))
				.collect(Collectors.toList());

			allFiles.stream()
				.map(file -> {
					try {
						var content = mapper.readValue(file.toFile(), BattleResults.class);

						if (shouldDelete) {
							Files.deleteIfExists(file);
						}

						return content;
					} catch (IOException e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.flatMap(list -> Arrays.stream(list.getData().getCoopResult().getHistoryGroups().getNodes()))
				.forEach(srRotationService::ensureDummyRotationExists);
		}
	}

	public void sendRotationsFromDatabase(boolean force) {
		var time = getSlotStartTime(Instant.now());

		vsModeDiscordChannelRepository.findAll().forEach(channel ->
			vsRotationSlotRepository.findByStartTime(time)
				.filter(slot -> slot.getRotation().getMode().equals(channel.getMode()))
				.filter(slot -> force || Math.abs(slot.getStartTime().getEpochSecond() - Instant.now().getEpochSecond()) <= 300)
				.ifPresent(slot -> sendVsRotationToDiscord(DiscordChannelDecisionMaker.chooseChannel(channel.getDiscordChannelName()), slot.getRotation())));

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
			.append(rotation.getStage().getName())
			.append("\n\n**Weapons**:\n");

		Stream.of(rotation.getWeapon1(), rotation.getWeapon2(), rotation.getWeapon3(), rotation.getWeapon4())
			.forEach(w -> builder.append("- ").append(w.getName()).append("\n"));

		builder.append("\nRotation will be running until **<t:")
			.append(rotation.getEndTime().getEpochSecond())
			.append(":f>** (<t:")
			.append(rotation.getEndTime().getEpochSecond())
			.append(":R>)");

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), rotation.getStage().getImage().getUrl());
	}

	public void refreshRotationsOld(boolean force) {
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
				rotationSchedulesResult.getData().getFestSchedules()
			), force);

			sendEventRotations(rotationSchedulesResult.getData().getEventSchedules(), force);

			sendSalmonRotations(rotationSchedulesResult.getData().getCoopGroupingSchedule(), force);
		} catch (JsonProcessingException e) {
//			logSender.sendLogs(logger, String.format("exception during rotation refresh!! %s", e.getMessage()));
			logger.error(e);
			logSender.sendLogs(logger, "An exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		}

		logger.info("Done posting rotations to discord");
	}

	private void sendSalmonRotations(CoopGroupingSchedule coopGroupingSchedule, boolean force) {
		String regularChannelName = DiscordChannelDecisionMaker.getS3SalmonRunChannel();
		String bigRunChannelName = DiscordChannelDecisionMaker.getS3SalmonRunBigRunChannel();
		String eggstraWorkChannelName = DiscordChannelDecisionMaker.getS3SalmonRunEggstraWorkChannelName();

		CoopRotation regularRotation = Arrays.stream(coopGroupingSchedule.getRegularSchedules().getNodes())
			.min(Comparator.comparing(CoopRotation::getStartTimeAsInstant))
			.orElse(null);

		CoopRotation bigRunRotation = Arrays.stream(coopGroupingSchedule.getBigRunSchedules().getNodes())
			.min(Comparator.comparing(CoopRotation::getStartTimeAsInstant))
			.orElse(null);

		CoopRotation eggstraWorkRotation = Arrays.stream(coopGroupingSchedule.getTeamContestSchedules().getNodes())
			.min(Comparator.comparing(CoopRotation::getStartTimeAsInstant))
			.orElse(null);

		if (regularRotation != null) {
			sendSalmonRotationToDiscord(regularChannelName, "Salmon Run", regularRotation, force);
		}

		if (bigRunRotation != null) {
			sendSalmonRotationToDiscord(bigRunChannelName, "Big Run", bigRunRotation, force);
		}

		if (eggstraWorkRotation != null) {
			sendSalmonRotationToDiscord(eggstraWorkChannelName, "Eggstra Work", eggstraWorkRotation, force);
		}
	}

	private void sendSalmonRotationToDiscord(String channelName, String srType, CoopRotation rotation, boolean force) {
		if ((force && rotation.getStartTimeAsInstant().isBefore(Instant.now()))
			|| (rotation.getStartTimeAsInstant().isBefore(Instant.now()) && rotation.getStartTimeAsInstant().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES)))
		) {
			StringBuilder builder = new StringBuilder(String.format("**%s**:\n\n**Stage**:\n- ", srType))
				.append(rotation.getSetting().getCoopStage().getName())
				.append("\n\n**Weapons**:\n");

			Arrays.stream(rotation.getSetting().getWeapons()).forEach(w ->
				builder.append("- ").append(w.getName()).append("\n"));

			builder.append("\nRotation will be running until **<t:")
				.append(rotation.getEndTimeAsInstant().getEpochSecond())
				.append(":f>** (<t:")
				.append(rotation.getEndTimeAsInstant().getEpochSecond())
				.append(":R>)");

			discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), rotation.getSetting().getCoopStage().getImage().getUrl());
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

					List<RotationMatchSettingWithTime> seriesRotations = getAnarchyRotationSettingsWithTimes(rotations, "CHALLENGE");
					List<RotationMatchSettingWithTime> openRotations = getAnarchyRotationSettingsWithTimes(rotations, "OPEN");

					sendRotationToDiscord(seriesChannelName, "Anarchy Series", seriesRotations);
					sendRotationToDiscord(openChannelName, "Anarchy Open", openRotations);
				} else if (currentRotation.getFestMatchSettings() != null) {
					String proChannelName = DiscordChannelDecisionMaker.getS3SplatfestProChannel();
					String openChannelName = DiscordChannelDecisionMaker.getS3SplatfestOpenChannel();

					List<RotationMatchSettingWithTime> proRotations = getSplatFestRotationSettingsWithTimes(rotations, "CHALLENGE");
					List<RotationMatchSettingWithTime> openRotations = getSplatFestRotationSettingsWithTimes(rotations, "REGULAR");

					sendRotationToDiscord(proChannelName, "Splatfest Pro", proRotations);
					sendRotationToDiscord(openChannelName, "Splatfest Open", openRotations);
				} else {
					String channelName = decideChannelToPostIn(currentRotation);

					if (channelName != null) {
						List<RotationMatchSettingWithTime> rotationMatchSettingsWithTimes = getRotationSettingsWithTimes(rotations);

						sendRotationToDiscord(channelName, getGameModeName(channelName), rotationMatchSettingsWithTimes);
					}
				}
			}
		}
	}

	private void sendEventRotations(RotationSchedulesResult.EventNodes eventSchedules, boolean force) {
		var nextRotation = eventSchedules.getNodes()[0];
		if (force || (
			Arrays.stream(nextRotation.getTimePeriods()).anyMatch(c ->
				c.getStartTimeAsInstant().isBefore(Instant.now())
					&& c.getStartTimeAsInstant().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))))
		) {
			// new rotation -> send notifications
			String channelName = DiscordChannelDecisionMaker.getS3ChallengeChannel();

			if (channelName != null) {
				sendChallengeRotationToDiscord(channelName, eventSchedules.getNodes());
			}
		}
	}

	private void sendRotationToDiscord(String channelName, String mode, List<RotationMatchSettingWithTime> rotations) {
		RotationMatchSettingWithTime firstRotation = rotations.stream().min(Comparator.comparing(RotationMatchSettingWithTime::getStartTime)).orElseThrow();

		if (firstRotation.getRotationMatchSetting() == null) {
			// Splatfest whenever a fest is not active.
			return;
		}

		String image1 = firstRotation.getRotationMatchSetting().getVsStages()[0].getImage().getUrl();
		String image2 = firstRotation.getRotationMatchSetting().getVsStages()[1].getImage().getUrl();

		StringBuilder builder = new StringBuilder("**").append(mode).append("**: ")
			.append("**").append(getEmoji(firstRotation.getRotationMatchSetting().getVsRule().getName())).append(firstRotation.getRotationMatchSetting().getVsRule().getName()).append("**\n")
			.append("- Stage A: **").append(firstRotation.getRotationMatchSetting().getVsStages()[0].getName()).append("**\n")
			.append("- Stage B: **").append(firstRotation.getRotationMatchSetting().getVsStages()[1].getName()).append("**\n\n")
			.append("**Next rotations**");

		rotations.stream()
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(RotationMatchSettingWithTime::getStartTime))
			.filter(r -> r.getRotationMatchSetting() != null && r.getRotationMatchSetting().getVsRule() != null && r.getRotationMatchSetting().getVsStages() != null)
			.skip(1)
			.forEach(r ->
				builder.append("\n- **<t:")
					.append(r.getStartTime().getEpochSecond())
					.append(":R>**: ")
					.append(getEmoji(r.getRotationMatchSetting().getVsRule().getName()))
					.append(r.getRotationMatchSetting().getVsRule().getName())
					.append(" --- **")
					.append(r.getRotationMatchSetting().getVsStages()[0].getName())
					.append("** --- **")
					.append(r.getRotationMatchSetting().getVsStages()[1].getName())
					.append("**")
			);

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1, image2);
	}

	private void sendChallengeRotationToDiscord(String channelName, RotationSchedulesResult.EventNode[] challenges) {
		var firstRotation = Arrays.stream(challenges).findFirst().orElseThrow();

		String image1 = firstRotation.getLeagueMatchSetting().getVsStages()[0].getImage().getUrl();
		String image2 = firstRotation.getLeagueMatchSetting().getVsStages()[1].getImage().getUrl();

		StringBuilder builder = new StringBuilder("**Challenge**:\n")
			.append("- Event: **").append(firstRotation.getLeagueMatchSetting().getLeagueMatchEvent().getName()).append("**\n")
			.append("- Description: **").append(firstRotation.getLeagueMatchSetting().getLeagueMatchEvent().getDesc()).append("**\n")
			.append("- Rules:\n```\n").append(firstRotation.getLeagueMatchSetting().getLeagueMatchEvent().getRegulation().replace("<br />", "\n")).append("\n```\n")
			.append("**Rotation details**\n- Game Rule: **").append(getEmoji(firstRotation.getLeagueMatchSetting().getVsRule().getName())).append(firstRotation.getLeagueMatchSetting().getVsRule().getName()).append("**\n")
			.append("- Stage A: **").append(firstRotation.getLeagueMatchSetting().getVsStages()[0].getName()).append("**\n")
			.append("- Stage B: **").append(firstRotation.getLeagueMatchSetting().getVsStages()[1].getName()).append("**\n\n");

		var futureSlots = Arrays.stream(firstRotation.getTimePeriods())
			.filter(t -> t.getStartTimeAsInstant().isAfter(Instant.now()))
			.sorted(Comparator.comparing(RotationSchedulesResult.TimePeriod::getStartTimeAsInstant))
			.collect(Collectors.toList());
		if (futureSlots.size() > 0) {
			builder.append("**Future Slots**");

			futureSlots.forEach(fs -> builder.append("\n- <t:")
				.append(fs.getStartTimeAsInstant().getEpochSecond()).append(":R>")
			);

			builder.append("\n\n");
		}

		builder.append("**Next challenges**");

		Arrays.stream(challenges)
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(RotationSchedulesResult.EventNode::getEarliestOccurrence))
			.filter(r -> r.getLeagueMatchSetting() != null && r.getLeagueMatchSetting().getVsRule() != null && r.getLeagueMatchSetting().getVsStages() != null)
			.skip(1)
			.forEach(r ->
				builder.append("\n- **<t:")
					.append(r.getEarliestOccurrence().getEpochSecond())
					.append(":f>** (<t:")
					.append(r.getEarliestOccurrence().getEpochSecond())
					.append(":R>) --- **")
					.append(r.getLeagueMatchSetting().getLeagueMatchEvent().getName())
					.append("** --- ")
					.append(getEmoji(r.getLeagueMatchSetting().getVsRule().getName()))
					.append(r.getLeagueMatchSetting().getVsRule().getName())
					.append(" --- **")
					.append(r.getLeagueMatchSetting().getVsStages()[0].getName())
					.append("** --- **")
					.append(r.getLeagueMatchSetting().getVsStages()[1].getName())
					.append("**")
			);

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1, image2);
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

	private String getGameModeName(String channelName) {
		String gameMode;

		if (channelName.equals(DiscordChannelDecisionMaker.getS3TurfWarChannel())) {
			gameMode = "Turf War";
		} else if (channelName.equals(DiscordChannelDecisionMaker.getS3AnarchyOpenChannel())) {
			gameMode = "Anarchy Open";
		} else if (channelName.equals(DiscordChannelDecisionMaker.getS3AnarchySeriesChannel())) {
			gameMode = "Anarchy Series";
		} else if (channelName.equals(DiscordChannelDecisionMaker.getS3SplatfestOpenChannel())) {
			gameMode = "Splatfest Open";
		} else if (channelName.equals(DiscordChannelDecisionMaker.getS3SplatfestProChannel())) {
			gameMode = "Splatfest Pro";
		} else if (channelName.equals(DiscordChannelDecisionMaker.getS3XRankChannel())) {
			gameMode = "X Battle";
		} else {
			gameMode = "Challenge";
		}

		return gameMode;
	}

	private List<RotationMatchSettingWithTime> getRotationSettingsWithTimes(List<Rotation> rotationSchedulesResult) {
		return rotationSchedulesResult.stream()
			.map(rsr -> new RotationMatchSettingWithTime(rsr.getStartTimeAsInstant(), getRotation(rsr)))
			.collect(Collectors.toList());
	}

	private List<RotationMatchSettingWithTime> getAnarchyRotationSettingsWithTimes(List<Rotation> rotationSchedulesResult, String mode) {
		var list = new ArrayList<RotationMatchSettingWithTime>();

		if (mode != null) {
			list.addAll(rotationSchedulesResult.stream()
				.filter(rsr -> rsr.getBankaraMatchSettings() != null)
				.map(rsr -> new RotationMatchSettingWithTime(rsr.getStartTimeAsInstant(),
					Arrays.stream(rsr.getBankaraMatchSettings())
						.filter(bms -> mode.equals(bms.getBankaraMode()))
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

	private List<RotationMatchSettingWithTime> getSplatFestRotationSettingsWithTimes(List<Rotation> rotationSchedulesResult, String mode) {
		var list = new ArrayList<RotationMatchSettingWithTime>();

		if (mode != null) {
			list.addAll(rotationSchedulesResult.stream()
				.filter(rsr -> rsr.getFestMatchSettings() != null)
				.map(rsr -> new RotationMatchSettingWithTime(rsr.getStartTimeAsInstant(),
					Arrays.stream(rsr.getFestMatchSettings())
						.filter(bms -> mode.equals(bms.getFestMode()))
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
			channelName = DiscordChannelDecisionMaker.getS3ChallengeChannel();
		} else if (currentRotation.getFestMatchSetting() != null && currentRotation.getFestMatchSetting().getVsStages() == null) {
			// other game mode DURING splatfest
			channelName = null;
		}

		return channelName;
	}

	private Instant getSlotStartTime(Instant base) {
		return base.atZone(ZoneOffset.UTC)
			.truncatedTo(ChronoUnit.DAYS)
			.withHour(base.atZone(ZoneOffset.UTC).getHour())
			.withMinute(0)
			.withSecond(0)
			.withNano(0)
			.toInstant();
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

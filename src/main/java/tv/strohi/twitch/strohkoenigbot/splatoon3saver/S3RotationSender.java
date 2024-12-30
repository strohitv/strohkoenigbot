package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.SplatNet3DataController;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3RotationSenderService;
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
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Transactional
@RequiredArgsConstructor
public class S3RotationSender implements ScheduledService {
	private final ObjectMapper mapper = new ObjectMapper();
	private final TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
	};

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	private final S3ApiQuerySender requestSender;
	private final DiscordBot discordBot;
	private final ExceptionLogger exceptionLogger;

	private final Splatoon3VsRotationService vsRotationService;
	private final Splatoon3SrRotationService srRotationService;

	private final Splatoon3RotationSenderService rotationSenderService;

	private final SplatNet3DataController splatNet3DataController;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3RotationSender_schedule")
			.schedule(CronSchedule.getScheduleString("5 0 * * * *"))
			.runnable(this::refreshRotations)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3RotationSender_boot_schedule")
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::refreshRotations)
			.build());
	}

	@Getter
	@Setter
	private boolean pauseSender = false;

	@Transactional
	public void refreshRotations() {
		refreshRotations(false);
	}

	@Transactional
	public void refreshRotations(boolean force) {
		if (pauseSender && !force) {
			logger.info("rotation sender is pause, returning early!");
			return;
		}

		var useNewWay = configurationRepository.findAllByConfigName("s3UseDatabase").stream()
			.map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
			.findFirst()
			.orElse(false);

		if (useNewWay) {
			importRotationsToDatabase();
			rotationSenderService.sendRotationsFromDatabase(force);
		} else {
			refreshRotationsOld(force);
		}
	}

	@Transactional
	public void importRotationsToDatabase() {
		var account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to import rotations to database!");
			return;
		}

		logger.info("Start importing rotations to database");
		try {
			importSrRotationsFromGameResultsFolder(account);

			var allRotationsResponse = requestSender.queryS3Api(account, S3RequestKey.RotationSchedules);
			var rotationSchedulesResult = mapper.readValue(allRotationsResponse, RotationSchedulesResult.class);

			splatNet3DataController.refresh(SplatNet3DataController.STORE_KEY, mapper.readValue(allRotationsResponse, typeRef));

			Arrays.stream(rotationSchedulesResult.getData().getVsStages().getNodes())
				.forEach(vsRotationService::ensureStageExists);

			Stream
				.of(rotationSchedulesResult.getData().getRegularSchedules(),
					rotationSchedulesResult.getData().getBankaraSchedules(),
					rotationSchedulesResult.getData().getXSchedules(),
					rotationSchedulesResult.getData().getFestSchedules())
				.flatMap(r -> Arrays.stream(r.getNodes()))
				.forEach(vsRotationService::ensureRotationExists);

			if (rotationSchedulesResult.getData().getCurrentFest() != null) {
				vsRotationService.ensureTricolorRotationsExist(rotationSchedulesResult.getData().getCurrentFest());
			}

			Arrays.stream(rotationSchedulesResult.getData().getEventSchedules().getNodes())
				.filter(r -> r.getLeagueMatchSetting().getVsRule() != null && r.getTimePeriods().length > 0)
				.forEach(vsRotationService::ensureRotationExists);

			Arrays.stream(rotationSchedulesResult.getData().getCoopGroupingSchedule().getRegularSchedules().getNodes())
				.forEach(r -> srRotationService.ensureRotationExists(r, "regularSchedules"));

			Arrays.stream(rotationSchedulesResult.getData().getCoopGroupingSchedule().getBigRunSchedules().getNodes())
				.forEach(r -> srRotationService.ensureRotationExists(r, "bigRunSchedules"));

			Arrays.stream(rotationSchedulesResult.getData().getCoopGroupingSchedule().getTeamContestSchedules().getNodes())
				.forEach(r -> srRotationService.ensureRotationExists(r, "teamContestSchedules"));
		} catch (JsonProcessingException e) {
//			logSender.sendLogs(logger, String.format("exception during rotation refresh!! %s", e.getMessage()));
			logger.error(e);
			logSender.sendLogs(logger, "An exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		} catch (IOException e) {
			logger.error(e);
			logSender.sendLogs(logger, "An IO exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		} catch (Exception e) {
			logger.error(e);
			logSender.sendLogs(logger, "An unspecified exception occurred during S3 rotation posting\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		}

		logger.info("Done posting rotations to discord");
	}

	@Transactional
	public void importSrRotationsFromGameResultsFolder(Account account) throws IOException {
		var accountUUIDHash = String.format("%05d", account.getId());
		importSrRotationsFromGameResultsFolder(accountUUIDHash);
	}

	@Transactional
	public void importSrRotationsFromGameResultsFolder(String accountUUIDHash) throws IOException {
		importSrRotationsFromGameResultsFolder(accountUUIDHash, false);
	}

	@Transactional
	public void importSrRotationsFromGameResultsFolder(String accountUUIDHash, boolean shouldDelete) throws IOException {
		var folderName = configurationRepository.findAllByConfigName("gameResultsFolder").stream()
			.map(Configuration::getConfigValue)
			.findFirst()
			.orElse(accountUUIDHash);

		Path directory = Path.of("game-results", folderName);
		if (Files.exists(directory)) {
			try (var fileList = Files.walk(directory)) {
				var allFiles = fileList
					.filter(f -> f.getFileName().toString().startsWith("Salmon_List_"))
					.collect(Collectors.toList());

				allFiles.stream()
					.flatMap(file -> {
						try {
							var content = mapper.readValue(file.toFile(), BattleResults.class);

							var result = Arrays.stream(content.getData().getCoopResult().getHistoryGroups().getNodes())
								.map(srRotationService::ensureDummyRotationExists)
								.filter(Objects::nonNull)
								.collect(Collectors.toList());

							if (shouldDelete) {
								Files.deleteIfExists(file);
							}

							return result.stream();
						} catch (IOException e) {
							logger.error("could not import salmon run rotations!");
							logger.error(e);
							return Stream.empty();
						}
					})
					.forEach((Splatoon3SrRotation rotation) -> logger.info("imported rotation: {}", rotation.getId()));
			}
		}
	}

	public void refreshRotationsOld(boolean force) {
		Account account = accountRepository.findByEnableSplatoon3(true).stream().findFirst().orElse(null);

		if (account == null) {
			logSender.sendLogs(logger, "No account found to refresh rotations!");
			return;
		}

		logger.info("Start posting rotations to discord");
		try {
			String allRotationsResponse = requestSender.queryS3Api(account, S3RequestKey.RotationSchedules);
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

		logger.info("Done posting rotations to discord using the old way");
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
				.append(rotation.getSetting().getCoopStage().getName());

			if (rotation.getSetting().getBoss() != null) {
				builder.append("\n\n**Boss**: \n- ").append(rotation.getSetting().getBoss().getName());
			}

			builder.append("\n\n**Weapons**:\n");

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
			.append("**").append(rotationSenderService.getEmoji(firstRotation.getRotationMatchSetting().getVsRule().getName())).append(firstRotation.getRotationMatchSetting().getVsRule().getName()).append("**\n")
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
					.append(":t>**")
					.append(" (<t:")
					.append(r.getStartTime().getEpochSecond())
					.append(":R>): ")
					.append(rotationSenderService.getEmoji(r.getRotationMatchSetting().getVsRule().getName()))
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
			.append("**Rotation details**\n- Game Rule: **").append(rotationSenderService.getEmoji(firstRotation.getLeagueMatchSetting().getVsRule().getName())).append(firstRotation.getLeagueMatchSetting().getVsRule().getName()).append("**\n")
			.append("- Stage A: **").append(firstRotation.getLeagueMatchSetting().getVsStages()[0].getName()).append("**\n")
			.append("- Stage B: **").append(firstRotation.getLeagueMatchSetting().getVsStages()[1].getName()).append("**\n\n");

		var futureSlots = Arrays.stream(firstRotation.getTimePeriods())
			.filter(t -> t.getStartTimeAsInstant().isAfter(Instant.now()))
			.sorted(Comparator.comparing(RotationSchedulesResult.TimePeriod::getStartTimeAsInstant))
			.collect(Collectors.toList());
		if (!futureSlots.isEmpty()) {
			builder.append("**Future Slots**");

			futureSlots.forEach(fs -> builder.append("\n- <t:")
				.append(fs.getStartTimeAsInstant().getEpochSecond())
				.append(":t>")
				.append(" (<t:")
				.append(fs.getStartTimeAsInstant().getEpochSecond())
				.append(":R>)")
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
					.append(rotationSenderService.getEmoji(r.getLeagueMatchSetting().getVsRule().getName()))
					.append(r.getLeagueMatchSetting().getVsRule().getName())
					.append(" --- **")
					.append(r.getLeagueMatchSetting().getVsStages()[0].getName())
					.append("** --- **")
					.append(r.getLeagueMatchSetting().getVsStages()[1].getName())
					.append("**")
			);

		discordBot.sendServerMessageWithImageUrls(channelName, builder.toString(), image1, image2);
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

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	private static class RotationMatchSettingWithTime {
		private Instant startTime;
		private RotationMatchSetting rotationMatchSetting;
	}
}

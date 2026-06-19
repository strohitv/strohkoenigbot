package tv.strohi.twitch.strohkoenigbot.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.BotStatus;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3ApiQuerySender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/bot")
@Log4j2
public class BotController implements ScheduledService {
	private final String BEELINK_BOOT_MESSAGE_CONFIG = "BotController_BeelinkMessageTime";
	private final int SECONDS_SHIFT = 25;
	private final int INTRO_TIME = 20;
	private final int OUTRO_TIME = 22;

	private final Bucket adminMessageBucket;
	private final Bucket loadLatestGameStringBucket;

	private final LogSender logSender;
	private final S3Downloader s3Downloader;
	private final TwitchBotClient twitchBotClient;

	private final S3ApiQuerySender s3ApiQuerySender;
	private final ObjectMapper mapper;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final Splatoon3VsResultRepository vsResultRepository;

	public BotController(LogSender logSender, S3Downloader s3Downloader, TwitchBotClient twitchBotClient, S3ApiQuerySender s3ApiQuerySender, ObjectMapper mapper, AccountRepository accountRepository, ConfigurationRepository configurationRepository, Splatoon3VsModeRepository modeRepository, Splatoon3VsResultRepository vsResultRepository) {
		this.logSender = logSender;
		this.s3Downloader = s3Downloader;
		this.twitchBotClient = twitchBotClient;
		this.s3ApiQuerySender = s3ApiQuerySender;
		this.mapper = mapper;
		this.accountRepository = accountRepository;
		this.configurationRepository = configurationRepository;
		this.modeRepository = modeRepository;
		this.vsResultRepository = vsResultRepository;

		var limit = Bandwidth.builder()
			.capacity(10)
			.refillGreedy(10, Duration.ofMinutes(1))
			.build();

		this.adminMessageBucket = Bucket.builder()
			.addLimit(limit)
			.build();

		this.loadLatestGameStringBucket = Bucket.builder()
			.addLimit(Bandwidth.builder()
				.capacity(5)
				.refillGreedy(5, Duration.ofMinutes(1))
				.build())
			.build();
	}

	@GetMapping()
	public BotStatus getBotStatus() {
		BotStatus status = new BotStatus();

		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());
		status.setRunning(twitchBotClient.isLive(account.getTwitchUserId()));

		return status;
	}

	@GetMapping("live-time")
	public long getLiveTime() {
		if (twitchBotClient.getWentLiveTime() != null) {
			var pauseDuration = twitchBotClient.getPauses().stream()
				.filter(p -> p[1] != null)
				.map(p -> Duration.between(p[0], p[1]))
				.reduce(Duration::plus)
				.orElse(Duration.ZERO);

			var currentPauseStart = twitchBotClient.getPauses().stream()
				.filter(p -> p[1] == null)
				.findFirst()
				.map(p -> p[0])
				.orElse(null);

			return Duration.between(
					twitchBotClient.getWentLiveTime(),
					currentPauseStart != null ? currentPauseStart : Instant.now())
				.minus(pauseDuration)
				.toSeconds();
		}

		return 0L;
	}

	@PostMapping("trigger-pause")
	public void triggerPause() {
		twitchBotClient.triggerPause();
	}

	@PostMapping("trigger-unpause")
	public void triggerUnpause() {
		twitchBotClient.triggerUnpause();
	}

	@GetMapping("last-stream-timestamps")
	public String getLastStreamTimestamps() {
		var builder = new StringBuilder("0:00:00 Intro");

		final var previousStreamStartTime = twitchBotClient.getPreviousStreamStartTime();
		final var previousStreamEndTime = twitchBotClient.getPreviousStreamEndTime();
		final var allPauses = twitchBotClient.getPauses();
		final var allGamesInStream = vsResultRepository.findByPlayedTimeBetween(previousStreamStartTime, previousStreamEndTime != null ? previousStreamEndTime : Instant.now());

		var gameNumber = 1;
		var endTimestamp = Duration.ZERO;
		for (var game : allGamesInStream) {
			final var wasDuringPause = allPauses.stream()
				.anyMatch(p -> p[0].isBefore(game.getPlayedTime()) && (p[1] == null || p[1].isAfter(game.getPlayedTime().plusSeconds(game.getDuration()))));

			if (wasDuringPause) {
				continue;
			}

			final var startingTime = allPauses.stream()
				.filter(p -> p[0].isBefore(game.getPlayedTime())
					&& p[1] != null
					&& p[1].isAfter(game.getPlayedTime())
					&& p[1].isBefore(game.getPlayedTime().plusSeconds(game.getDuration())))
				.findFirst()
				.map(p -> p[0])
				.orElse(game.getPlayedTime());

			final var allPausesBefore = allPauses.stream()
				.filter(p -> p[0].isBefore(startingTime) && p[1] != null && p[1].isBefore(startingTime))
				.map(p -> Duration.between(p[0], p[1]))
				.reduce(Duration::plus)
				.orElse(Duration.ZERO);

			final var startTimestamp = Duration.between(previousStreamStartTime, startingTime)
				.minus(allPausesBefore)
				.plusSeconds(SECONDS_SHIFT) // because for some reason, timestamps are off ~25 seconds
				.minusSeconds(INTRO_TIME);

			final var timeDifference = Duration.between(game.getPlayedTime(), startingTime).toSeconds();

			final var allPausesBetween = allPauses.stream()
				.filter(p -> p[0].isAfter(startingTime) && p[1] != null && p[0].isBefore(game.getPlayedTime().plusSeconds(game.getDuration())))
				.map(p -> Duration.between(p[0], p[1]))
				.reduce(Duration::plus)
				.orElse(Duration.ZERO);

			endTimestamp = startTimestamp.plusSeconds(Math.max(5, game.getDuration()))
				.minus(allPausesBetween)
				.minusSeconds(timeDifference)
				.plusSeconds(INTRO_TIME)
				.plusSeconds(OUTRO_TIME);

			final var ownPlayer = game.getTeams().stream()
				.filter(Splatoon3VsResultTeam::getIsMyTeam)
				.flatMap(t -> t.getTeamPlayers().stream())
				.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
				.findFirst();

			builder.append("\n")
				.append(startTimestamp.toHoursPart())
				.append(":")
				.append(String.format("%02d", startTimestamp.toMinutesPart()))
				.append(":")
				.append(String.format("%02d", startTimestamp.toSecondsPart()))
				.append(" #")
				.append(gameNumber)
				.append(": ")
				.append(game.getOwnJudgement())
				.append(" ");

			gameNumber++;

			var betweenChar = "";
			for (var team : game.getTeams()) {
				var points = team.getScore() != null
					? String.format("%d", team.getScore())
					: team.getPaintRatio() != null ? String.format("%.1f%%", team.getPaintRatio() * 100) : "??";

				builder.append(betweenChar).append(points);
				betweenChar = "-";
			}

			builder
				.append(game.getKnockout() != null && !"NEITHER".equals(game.getKnockout()) ? " KO - " : " - ")
				.append(ownPlayer.map(o -> o.getWeapon().getName()).orElse("UNKNOWN WEAPON"))
				.append(" - ")
				.append(game.getMode().getName())
				.append(" - ")
				.append(game.getRule().getName())
				.append(" - ")
				.append(game.getStage().getName());
		}

		if (!endTimestamp.isZero()) {
			builder.append("\n")
				.append(endTimestamp.toHoursPart())
				.append(":")
				.append(String.format("%02d", endTimestamp.toMinutesPart()))
				.append(":")
				.append(String.format("%02d", endTimestamp.toSecondsPart()))
				.append(" Outro");
		}

		return builder.toString().trim();
	}

	@GetMapping("latest-game-string")
	public String getLatestGameString() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		if (loadLatestGameStringBucket.tryConsume(1) && twitchBotClient.getWentLiveTime() != null) {
			try {
				var latestGamesResponse = s3ApiQuerySender.queryS3Api(account, S3RequestKey.Latest);
				var latestGames = mapper.readValue(latestGamesResponse, BattleResults.class);

				var latestGame = Arrays.stream(latestGames.getData().getLatestBattleHistories().getHistoryGroups().getNodes())
					.findFirst()
					.stream()
					.flatMap(hgn -> Arrays.stream(hgn.getHistoryDetails().getNodes()))
					.findFirst()
					.orElse(null);

				if (latestGame != null) {
					return String.format("%s - %s - %s - %s - %s",
						latestGame.getJudgement(),
						latestGame.getPlayer().getWeapon().getName(),
						modeRepository.findByApiId(latestGame.getVsMode().getId())
							.map(Splatoon3VsMode::getName)
							.orElse("Unknown Mode"),
						latestGame.getVsRule().getName(),
						latestGame.getVsStage().getName());
				}
			} catch (JsonProcessingException ignored) {
			}
		}

		return "";
	}


	@GetMapping("previous-recording-name")
	public String getPreviousRecordingName() {
		final var previousStreamStartTime = twitchBotClient.getPreviousStreamStartTime();

		final var allGamesInStream = vsResultRepository.findByPlayedTimeBetween(previousStreamStartTime, Instant.now());

		final var allWeaponsSortedByUsage = allGamesInStream.stream()
			.flatMap(g -> g.getTeams().stream())
			.filter(Splatoon3VsResultTeam::getIsMyTeam)
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.map(p -> p.getWeapon().getName())
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
			.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
			.collect(Collectors.toList());

		final var allModesSortedByOccurrence = allGamesInStream.stream()
			.map(g -> g.getMode().getName())
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
			.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
			.collect(Collectors.toList());

		final var streamDate = previousStreamStartTime
			.atZone(ZoneId.of("Europe/Berlin"))
			.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

		final var titleFormat = String.format("Splatoon 3 %%s in %%s vom %s", streamDate);

		final var includedModes = new ArrayList<String>();
		for (var modeWithCount : allModesSortedByOccurrence) {
			final var mode = modeWithCount.getKey();

			if (includedModes.size() < 3
				&& Stream.concat(includedModes.stream(), Stream.of(mode))
				.map(this::shortenModeName)
				.reduce((a, b) -> String.format("%s, %s", a, b))
				.orElse("").length() <= 20) {

				includedModes.add(shortenModeName(mode));
			} else {
				includedModes.add("etc");
				break;
			}
		}

		final var modeString = includedModes.stream()
			.reduce((a, b) -> String.format("%s, %s", a, b))
			.orElse("")
			.replace(", etc", " etc");

		final var includedWeapons = new ArrayList<String>();
		for (var weaponWithCount : allWeaponsSortedByUsage) {
			final var weapon = weaponWithCount.getKey();

			if (String.format(titleFormat, Stream.concat(includedWeapons.stream(), Stream.of(weapon))
				.reduce((a, b) -> String.format("%s, %s", a, b))
				.orElse(""), modeString).length() < 96) {
				includedWeapons.add(weapon);
			} else {
				includedWeapons.add("etc");
				break;
			}
		}

		var weaponsString = includedWeapons.stream()
			.reduce((a, b) -> String.format("%s, %s", a, b))
			.orElse("")
			.replace(", etc", " etc");

		if (weaponsString.isBlank()) {
			weaponsString = String.format("%d", new Random().nextInt(100000));
		}

		return String.format(titleFormat, weaponsString, modeString);
	}

	private String shortenModeName(String mode) {
		switch (mode) {
			case "Regular Battle":
				return "Turf War";
			case "Anarchy Series":
				return "Series";
			case "Anarchy Open":
				return "Open";
			case "X Battle":
				return "X Rank";
//			case "Challenge":
//				return "Challenge";
			case "Splatfest Open":
				return "SF Open";
			case "Splatfest Pro":
				return "SF Pro";
			case "Splatfest Tricolor":
				return "SF Tricolor";
//			case "Private Battle":
//				return "PB";
			default:
				return mode;
		}
	}

	@PostMapping("start")
	public void startExporter() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		if (!twitchBotClient.isLive(account.getTwitchUserId())) {
			twitchBotClient.goLive(account.getTwitchUserId());
		}
	}

	@PostMapping("import")
	public void importGames() {
		log.info("Instance `{}`, debug = `{}` is importing games triggered by BotController", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());
		s3Downloader.downloadBattles(true);
	}

	@PostMapping("stop")
	public void stopExporter() {
		Account account = accountRepository.findAll().stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.findFirst()
			.orElse(new Account());

		twitchBotClient.goOffline(account.getTwitchUserId());
	}

	@PostMapping("admin-message")
	public ResponseEntity<Void> sendAdminMessage(@RequestBody @NonNull String message) {
		if (!adminMessageBucket.tryConsume(1)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}

		if (message.length() > 1800) {
			ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}

		if ("Beelink booted successfully.".equals(message)) {
			var config = configurationRepository.findByConfigName(BEELINK_BOOT_MESSAGE_CONFIG)
				.orElseGet(() -> configurationRepository.save(Configuration.builder()
					.configName(BEELINK_BOOT_MESSAGE_CONFIG)
					.configValue(String.format("%d", Instant.now().getEpochSecond()))
					.build()));

			config.setConfigValue(String.format("%d", Instant.now().getEpochSecond()));
			configurationRepository.save(config);
		} else {
			logSender.queueLogs(log, String.format("Instance `%s`, debug = `%s` received message via web interface:\n```\n%s\n```", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), message));
		}

		return ResponseEntity.ok().build();
	}

	private void checkBeelinkActivity() {
		var config = configurationRepository.findByConfigName(BEELINK_BOOT_MESSAGE_CONFIG)
			.orElseGet(() -> configurationRepository.save(Configuration.builder()
				.configName(BEELINK_BOOT_MESSAGE_CONFIG)
				.configValue(String.format("%d", Instant.now().getEpochSecond()))
				.build()));

		var lastMessageTimestamp = Instant.ofEpochSecond(Long.parseLong(config.getConfigValue()));

		if (Instant.now().isAfter(lastMessageTimestamp.plus(6, ChronoUnit.HOURS))) {
			logSender.queueLogs(log, String.format("## WARNING\nInstance `%s`, debug = `%s` did **not** receive a message from Beelink for 6 hours! Last message EpochSecond: `%s`", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug(), config.getConfigValue()));
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("BotController_checkBeelinkTiming")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyHours(6)))
			.runnable(this::checkBeelinkActivity)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

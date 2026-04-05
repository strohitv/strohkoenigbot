package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsInksightPlayerStats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsInksightPlayerStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.InksightReplay;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ReplayResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static tv.strohi.twitch.strohkoenigbot.utils.Constants.ALL_TWITCH_CHANNEL_NAMES;

@Component
@Log4j2
@RequiredArgsConstructor
public class S3ReplayCodeLoader implements ScheduledService {
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final TwitchBotClient twitchBotClient;
	private final TwitchMessageSender twitchMessageSender;

	private final S3ApiQuerySender apiQuerySender;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;
	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3VsInksightPlayerStatsRepository inksightPlayerStatsRepository;

	private void downloadReplaysDuringStream() {
		if (twitchBotClient.getWentLiveTime() != null) {
			downloadReplays();
		}
	}

	private void downloadReplays() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var replaysResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.Replays);

			try {
				var replays = objectMapper.readValue(replaysResponse, ReplayResult.class);
				var allReplays = replays.getData().getReplays().getNodes();

				for (var replay : allReplays) {
					resultRepository.findByPlayedTimeBetween(
							replay.getHistoryDetail().getPlayedTimeAsInstant().minus(10, ChronoUnit.MINUTES),
							replay.getHistoryDetail().getPlayedTimeAsInstant().plus(10, ChronoUnit.MINUTES))
						.stream()
						.filter(result -> result.getReplayCode() == null || result.getReplayCode().isBlank())
						.filter(result -> result.apiIdEquals(replay.getHistoryDetail().getId()))
						.findFirst()
						.ifPresent(result -> {
							resultRepository.save(result.toBuilder()
								.replayCode(replay.getReplayCode())
								.build());

							logSender.queueLogs(log, "Replay code `%s` was added to battle with id `%d`.", replay.getReplayCode(), result.getId());
						});
				}

				System.out.println("done");
			} catch (Exception ex) {
				exceptionLogger.logExceptionAsAttachment(log, "could not refresh weapon stats", ex);
			}
		}
	}

	public List<String> getReplayQueue() {
		return resultRepository.findAllByReplayCodeNotNullAndMmrLoadFailedFalseAndReplayJsonNull()
			.stream()
			.map(Splatoon3VsResult::getReplayCode)
			.collect(Collectors.toList());
	}

	@Transactional
	public boolean addReplayData(String replayCode, String replayJson) {
		var foundReplay = resultRepository.findByReplayCodeAndMmrLoadFailedFalseAndReplayJsonNull(replayCode);

		if (foundReplay.isPresent()) {
			var result = foundReplay.get();
			if (replayJson == null || "ERROR".equalsIgnoreCase(replayJson)) {
				logSender.queueLogs(log, "ERROR: Could not save InkSight replay for replay code `%s`", replayCode);

				resultRepository.save(result.toBuilder()
					.mmrLoadFailed(true)
					.build());

				return false;
			}

			try {
				var inksightData = objectMapper.readValue(replayJson, InksightReplay.class);

				var myself = inksightData.getTeams().stream()
					.flatMap(t -> t.getPlayers().stream())
					.filter(InksightReplay.PlayerData::getIsRecorder)
					.findFirst()
					.orElseThrow();

				var mmr = myself.getStats().getMmr().orElse(result.getMmr());
				var innerMmr = myself.getStats().getInnerMmr();
				var power = myself.getStats().getXPower().orElse(myself.getStats().getMmr().orElse(result.getPower()));
				var zonesXP = myself.getStats().getXPowerZones();
				var towerXP = myself.getStats().getXPowerTower();
				var rainXP = myself.getStats().getXPowerRain();
				var clamsXP = myself.getStats().getXPowerClams();
				var playerLevel = myself.getStats().getPlayerLevel();
				var alivePct = myself.getStats().getAlivePct();

				var savedResult = resultRepository.save(result.toBuilder()
					.inksightJsonVersion(inksightData.getVersion())
					.replayJson(replayJson)
					.mmr(mmr)
					.innerMmr(innerMmr)
					.power(power)
					.xPowerZones(zonesXP)
					.xPowerTower(towerXP)
					.xPowerRain(rainXP)
					.xPowerClams(clamsXP)
					.alivePct(alivePct)
					.playerLevel(playerLevel)
					.build());

				var gameDuration = Duration.ofSeconds(result.getDuration());
				var ownAliveDuration = Duration.ofSeconds((int) (result.getDuration() * alivePct / 100));
				var ownDeadDuration = gameDuration.minus(ownAliveDuration);

				logSender.queueLogs(log, "# Found new InkSight replay\n- version: `%d`\n- player: `%s#%s`\n- mmr: `%.1f`\n- power: `%.1f`\n- (game time): `%02d:%02d`",
					inksightData.getVersion(),
					myself.getName(),
					myself.getDiscriminator(),
					mmr,
					power,
					gameDuration.toMinutesPart(),
					gameDuration.toSecondsPart());

				for (var channelName : ALL_TWITCH_CHANNEL_NAMES) {
					twitchMessageSender.send(channelName, String.format("Found new stats for player %s#%s: mmr = %.1f, power = %.1f, alive time = %02d:%02d, dead time = %02d:%02d",
						myself.getName(),
						myself.getDiscriminator(),
						mmr,
						power,
						ownAliveDuration.toMinutesPart(),
						ownAliveDuration.toSecondsPart(),
						ownDeadDuration.toMinutesPart(),
						ownDeadDuration.toSecondsPart()));
				}

				if (inksightData.getHasFlag()) {
					var flaggedPlayers = inksightData.getTeams().stream()
						.flatMap(t -> t.getPlayers().stream())
						.filter(p -> !p.getAnticheat().getInternalReports().isEmpty())
						.collect(Collectors.toList());

					for (var player : flaggedPlayers) {
						logSender.queueLogs(log, "### Found notes on replay\n- player `%s#%s`:\n- %s", player.getName(), player.getDiscriminator(), player.getAnticheat().getInternalReports().stream().reduce((a, b) -> String.format("%s\n- %s", a, b)).orElse(""));

						for (var channelName : ALL_TWITCH_CHANNEL_NAMES) {
							twitchMessageSender.send(channelName, String.format("Found notes on player %s#%s: %s", player.getName(), player.getDiscriminator(), player.getAnticheat().getInternalReports().stream().reduce((a, b) -> String.format("%s, %s", a, b)).orElse("")));
						}
					}
				}

				var allPlayers = inksightData.getTeams().stream()
					.flatMap(t -> t.getPlayers().stream())
					.collect(Collectors.toList());
				var allPlayersFromResult = savedResult.getTeams().stream()
					.flatMap(t -> t.getTeamPlayers().stream())
					.collect(Collectors.toList());

				for (var player : allPlayers) {
					var playerFromResult = allPlayersFromResult.stream()
						.filter(p -> p.getName().trim().equals(player.getName().trim()) && p.getNameId().trim().equals(player.getDiscriminator().trim()))
						.findFirst()
						.orElse(null);

					if (playerFromResult == null) {
						logSender.queueLogs(log, "### ERROR during inksight player stats entry creation\n- player `%s#%s` was not in the game\n- result id: `%d`", player.getName(), player.getDiscriminator(), result.getId());
						continue;
					}

					var playerMmr = player.getStats().getMmr().orElse(result.getMmr());
					var playerInnerMmr = player.getStats().getInnerMmr();
					var playerPower = player.getStats().getXPower().orElse(player.getStats().getMmr().orElse(null));
					var playerZonesXP = player.getStats().getXPowerZones();
					var playerTowerXP = player.getStats().getXPowerTower();
					var playerRainXP = player.getStats().getXPowerRain();
					var playerClamsXP = player.getStats().getXPowerClams();
					var playerPlayerLevel = player.getStats().getPlayerLevel();
					var playerAlivePct = player.getStats().getAlivePct();

					var savedPlayerStats = inksightPlayerStatsRepository.save(Splatoon3VsInksightPlayerStats.builder()
						.power(playerPower)
						.mmr(playerMmr)
						.innerMmr(playerInnerMmr)
						.xPowerZones(playerZonesXP)
						.xPowerTower(playerTowerXP)
						.xPowerRain(playerRainXP)
						.xPowerClams(playerClamsXP)
						.playerLevel(playerPlayerLevel)
						.alivePct(playerAlivePct)
						.result(savedResult)
						.player(playerFromResult.getPlayer())
						.build());

					var aliveDuration = Duration.ofSeconds((int) (result.getDuration() * savedPlayerStats.getAlivePct() / 100));
					var deadDuration = gameDuration.minus(aliveDuration);
					logSender.queueLogs(log, String.format("### Found new Inksight player stats\n" +
							"- Stats id: `%d`\n" +
							"- Result id: `%d`\n" +
							"- Player: id = `%d`, name = `%s#%s`\n" +
							"- Power: `%.1f`\n" +
							"- MMR: `%.1f`\n" +
							"- Inner MMR: `%.1f`\n" +
							"- XP Splat Zones: `%.1f`\n" +
							"- XP Tower Control: `%.1f`\n" +
							"- XP Rainmaker: `%.1f`\n" +
							"- XP Clam Blitz: `%.1f`\n" +
							"- Player Level: `%d`\n" +
							"- Alive Percentage: `%.1f`\n" +
							"= Alive time: `%02d:%02d`\n" +
							"= Dead time: `%02d:%02d`",
						savedPlayerStats.getId(),
						result.getId(),
						playerFromResult.getPlayerId(),
						player.getName(),
						player.getDiscriminator(),
						savedPlayerStats.getPower(),
						savedPlayerStats.getMmr(),
						savedPlayerStats.getInnerMmr(),
						savedPlayerStats.getXPowerZones(),
						savedPlayerStats.getXPowerTower(),
						savedPlayerStats.getXPowerRain(),
						savedPlayerStats.getXPowerClams(),
						savedPlayerStats.getPlayerLevel(),
						savedPlayerStats.getAlivePct(),
						aliveDuration.toMinutesPart(),
						aliveDuration.toSecondsPart(),
						deadDuration.toMinutesPart(),
						deadDuration.toSecondsPart()));
				}

				return true;
			} catch (Exception ex) {
				if (ex instanceof UnrecognizedPropertyException) {
					logSender.sendLogsAsAttachment(log, "Could not parse replayJson!", replayJson);
				}

				resultRepository.save(result.toBuilder()
					.mmrLoadFailed(true)
					.build());
				exceptionLogger.logExceptionAsAttachment(log, "Error during adding replay json to ", ex);
			}
		}

		return false;
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
				.name("S3ReplayCodeLoader_loadDuringStream")
				.schedule(TickSchedule.getScheduleString(6))
				.runnable(this::downloadReplaysDuringStream)
				.build(),
			ScheduleRequest.builder()
				.name("S3ReplayCodeLoader_loadEveryHour")
				.schedule(TickSchedule.getScheduleString(TickSchedule.everyHours(1)))
				.runnable(this::downloadReplays)
				.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

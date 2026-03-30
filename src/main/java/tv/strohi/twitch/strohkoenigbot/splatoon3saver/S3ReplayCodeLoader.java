package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.InksightReplay;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ReplayResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

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

	public boolean addReplayData(String replayCode, String replayJson) {
		var foundReplay = resultRepository.findByReplayCodeAndMmrLoadFailedFalseAndReplayJsonNull(replayCode);

		if (foundReplay.isPresent()) {
			var result = foundReplay.get();
			if (replayJson == null || "ERROR".equalsIgnoreCase(replayJson)) {
				logSender.queueLogs(log, "ERROR: Could not save InkSight replay for replay code `%s``", replayCode);

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
				var power = myself.getStats().getXPower().orElse(myself.getStats().getMmr().orElse(result.getPower()));

				resultRepository.save(result.toBuilder()
					.replayJson(replayJson)
					.mmr(mmr)
					.power(power)
					.build());

				logSender.queueLogs(log, "Received and saved new InkSight replay for player `%s` with mmr `%.1f` and power `%.1f`", myself.getName(), mmr, power);

				for (var channelName : ALL_TWITCH_CHANNEL_NAMES) {
					twitchMessageSender.send(channelName, String.format("Found new stats for player %s: mmr = %.1f, power = %.1f", myself.getName(), mmr, power));
				}

				if (inksightData.getHasFlag()) {
					var flaggedPlayers = inksightData.getTeams().stream()
						.flatMap(t -> t.getPlayers().stream())
						.filter(p -> !p.getAnticheat().getInternalReports().isEmpty())
						.collect(Collectors.toList());

					for (var player : flaggedPlayers) {
						logSender.queueLogs(log, "Found notes on player `%s`:\n- %s", player.getName(), player.getAnticheat().getInternalReports().stream().reduce((a, b) -> String.format("%s\n- %s", a, b)).orElse(""));

						for (var channelName : ALL_TWITCH_CHANNEL_NAMES) {
							twitchMessageSender.send(channelName, String.format("Found notes on player %s: %s", player.getName(), player.getAnticheat().getInternalReports().stream().reduce((a, b) -> String.format("%s, %s", a, b)).orElse("")));
						}
					}
				}

				return true;
			} catch (Exception ex) {
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

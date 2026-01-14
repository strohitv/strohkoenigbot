package tv.strohi.twitch.strohkoenigbot.sendou;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.sendou.model.in.MatchId;
import tv.strohi.twitch.strohkoenigbot.sendou.model.in.SendouQMatch;
import tv.strohi.twitch.strohkoenigbot.sendou.model.in.SendouUser;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SendouService implements ScheduledService {
	private final static URI API_URL = URI.create("https://sendou.ink/");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	private final AccountRepository accountRepository;

	private final TwitchBotClient twitchBotClient;
	private final S3StreamStatistics streamStatistics;

	private final Map<Long, SendouUser> players = new HashMap<>();

	public Optional<SendouMatch> loadActiveMatch(Account account) {
		return loadActiveSendouQMatchId(account)
			.flatMap(matchId -> loadSendouQMatch(account, matchId.getMatchId()))
			.flatMap(match -> mapToActiveMatch(account, match));
	}

	public Optional<MatchId> loadActiveSendouQMatchId(Account account) {
		var matchIdResponse = this.generateRequest(account, MatchId.class, "/api/sendouq/active-match/%s", account.getSendouId());

		if (matchIdResponse == null || matchIdResponse.getMatchId() == null) {
			return Optional.empty();
		}

		return Optional.of(matchIdResponse);
	}

	public Optional<SendouQMatch> loadSendouQMatch(Account account, Long matchId) {
		var sendouQMatchResponse = this.generateRequest(account, SendouQMatch.class, "/api/sendouq/match/%d", matchId);

		if (sendouQMatchResponse == null || sendouQMatchResponse.getTeamAlpha() == null) {
			// no match active
			return Optional.empty();
		}

		return Optional.of(sendouQMatchResponse);
	}

	public Optional<SendouMatch> mapToActiveMatch(@NotNull Account account, @NotNull SendouQMatch sendouQMatch) {
		var ownTeamIsAlpha = sendouQMatch.getTeamAlpha().getPlayers().stream()
			.anyMatch(p -> account.getSendouId().equals(p.getUserId()));

		var ownTeam = ownTeamIsAlpha ? sendouQMatch.getTeamAlpha() : sendouQMatch.getTeamBravo();
		var otherTeam = ownTeamIsAlpha ? sendouQMatch.getTeamBravo() : sendouQMatch.getTeamAlpha();

		var ownSendouTeam = SendouTeam.builder()
			.name(ownTeamIsAlpha ? "Team Alpha" : "Team Bravo")
			.players(ownTeam.getPlayers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(p.getUserId().equals(account.getSendouId()))
					.build())
				.collect(Collectors.toList()))
			.build();

		var opponentSendouTeam = SendouTeam.builder()
			.name(ownTeamIsAlpha ? "Team Bravo" : "Team Alpha")
			.players(otherTeam.getPlayers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(p.getUserId().equals(account.getSendouId()))
					.build())
				.collect(Collectors.toList()))
			.build();

		var mapModes = sendouQMatch.getMapList().stream()
			.map(m -> MapMode.builder()
				.mode(MatchMode.fromString(m.getMap().getMode()))
				.stage(SendouMap.builder().id(m.getMap().getStage().getId()).name(m.getMap().getStage().getName()).build())
				.finished(m.getWinnerTeamId() != null)
				.ownTeamWon(ownTeam.getId().equals(m.getWinnerTeamId()))
				.ownScore(m.getPoints() != null && m.getPoints().size() >= 2 ? ownTeamIsAlpha ? m.getPoints().get(0) : m.getPoints().get(1) : null)
				.ownTeamPlayers(m.getParticipatedUserIds() != null
					? ownSendouTeam.getPlayers().stream().filter(p -> m.getParticipatedUserIds().contains(p.getId())).collect(Collectors.toList())
					: ownSendouTeam.getPlayers())
				.opponentScore((m.getPoints() != null && m.getPoints().size() >= 2 ? ownTeamIsAlpha ? m.getPoints().get(1) : m.getPoints().get(0) : null))
				.opponentTeamPlayers(m.getParticipatedUserIds() != null
					? opponentSendouTeam.getPlayers().stream().filter(p -> m.getParticipatedUserIds().contains(p.getId())).collect(Collectors.toList())
					: opponentSendouTeam.getPlayers())
				.pickReason(PickReason.determine(m.getSource(), sendouQMatch.getTeamAlpha().getId()))
				.build())
			.collect(Collectors.toList());

		var result = SendouMatch.builder()
			.matchActive(true)
			.type(MatchType.SENDOU_Q)
			.ownScore(ownTeam.getScore())
			.opponentScore(otherTeam.getScore())
			.myself(ownSendouTeam.getPlayers().stream().filter(SendouPlayer::isMyself).findFirst().orElseThrow())
			.ownTeam(ownSendouTeam)
			.opponentTeam(opponentSendouTeam)
			.mapModes(mapModes)
			.build();

		return Optional.of(result);
	}

	private SendouUser getSendouUser(Account account, Long playerId) {
		if (!players.containsKey(playerId)) {
			var userResponse = this.generateRequest(account, SendouUser.class, "/api/user/%d", playerId);

			if (userResponse == null || userResponse.getId() == null) {
				// no user found
				return SendouUser.builder()
					.name("unknown Player")
					.build();
			}

			players.put(playerId, userResponse);
		}

		return players.get(playerId);
	}

	private void refreshSendouQResults() {
		if (twitchBotClient.getWentLiveTime() == null) {
			return;
		}

		accountRepository.findByIsMainAccount(true).stream()
			.findFirst()
			.flatMap(this::loadActiveMatch)
			.ifPresent(streamStatistics::setSendouMatchResult);
	}

	private <T> T generateRequest(Account account, Class<T> classToConvert, String path, Object... params) {
		try {
			var request = HttpRequest.newBuilder()
				.GET()
				.uri(API_URL.resolve(String.format(path, params)))
				.setHeader("Authorization", String.format("Bearer %s", account.getSendouApiToken()))
				.build();

			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() >= 200 && response.statusCode() <= 300) {
				var body = new String(response.body());
				return objectMapper.readValue(body, classToConvert);
			} else {
				return null;
			}

		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("SendouService_loadSendouGames")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyMinutes(1)))
			.runnable(this::refreshSendouQResults)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

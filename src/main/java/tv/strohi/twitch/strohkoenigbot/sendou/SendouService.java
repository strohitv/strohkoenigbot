package tv.strohi.twitch.strohkoenigbot.sendou;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.sendou.model.in.*;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.*;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.MapMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

	@Setter
	private Long sendouAccountId = null;
	@Setter
	private Long tournamentId = null;
	@Setter
	private boolean searchSendouQ = false;

	public Optional<SendouMatch> loadActiveMatch(Account account, @NonNull Long sendouAccountId, Long tournamentId, boolean searchSendouQ) {
		var tournamentMatch = Optional.<SendouMatch>empty();

		if (tournamentId != null) {
			var foundTournament = loadTournament(account, tournamentId);

			if (foundTournament.isPresent()) {
				// todo caching -> am besten über die generateRequest Methode
				var tournament = foundTournament.get();

				// caching probably not possible thanks to subs
				var allTeams = loadTournamentTeams(account, tournamentId);

				var teamOfPlayer = allTeams
					.stream()
					.flatMap(l -> l.stream().filter(t -> t.getMembers().stream().anyMatch(m -> Objects.equals(m.getUserId(), sendouAccountId))))
					.findFirst();

				var teamIdOfPlayer = teamOfPlayer.map(SendouTournamentTeam::getId);

				var winCondition = "";
				var currentMatchId = 0L;
				var currentMatchStartedAt = 0L;
				var bracketNumber = 0;
				while (tournament.hasStarted() && teamIdOfPlayer.isPresent() && bracketNumber < tournament.getBrackets().size()) {
					var teamId = teamIdOfPlayer.get();

					// todo caching (brackets where all status are COMPLETED (4) or LOCKED (0))
					var bracket = loadTournamentBracket(account, tournamentId, bracketNumber);
					var allGamesOfBracket = bracket
						.stream()
						.flatMap(b -> b.getData().getMatch().stream())
						.collect(Collectors.toList());

					var allGamesOfTeam = allGamesOfBracket.stream()
						.filter(m -> m.getOpponent1() != null && m.getOpponent2() != null)
						.filter(m -> Objects.equals(m.getOpponent1().getId(), teamId) || Objects.equals(m.getOpponent2().getId(), teamId))
						.collect(Collectors.toList());

					var runningGameOfTeam = allGamesOfTeam.stream()
						.filter(m -> SendouTournamentMatchStatus.RUNNING.equals(m.getStatus()))
						.findFirst();

					if (runningGameOfTeam.isPresent()) {
						var game = runningGameOfTeam.get();

						currentMatchId = game.getId();
						winCondition = bracket.get().getData().getRound().stream()
							.filter(b -> Objects.equals(b.getId(), game.getRound_id()))
							.findFirst()
							.map(r -> "BEST_OF".equals(r.getMaps().getType()) ? String.format("bo%d", r.getMaps().getCount()) : String.format("pa%d", r.getMaps().getCount()))
							.orElse("");
						break;
					}

					var newestCompletedGame = allGamesOfTeam.stream()
						.filter(m -> SendouTournamentMatchStatus.COMPLETED.equals(m.getStatus()))
						.filter(m -> !tournament.getIsFinalized() || Instant.now().isBefore(Instant.ofEpochSecond(m.getStartedAt()).plus(90, ChronoUnit.MINUTES)))
						.max(Comparator.comparing(SendouTournamentBracket.SendouTournamentBracketDataMatch::getStartedAt));

					if (newestCompletedGame.isPresent() && newestCompletedGame.get().getStartedAt() > currentMatchStartedAt) {
						var game = newestCompletedGame.get();
						currentMatchId = game.getId();
						currentMatchStartedAt = game.getStartedAt();

						winCondition = bracket.get().getData().getRound().stream()
							.filter(b -> Objects.equals(b.getId(), game.getRound_id()))
							.findFirst()
							.map(r -> "BEST_OF".equals(r.getMaps().getType()) ? String.format("bo%d", r.getMaps().getCount()) : String.format("pa%d", r.getMaps().getCount()))
							.orElse("");
					}

					bracketNumber++;
				}

				if (currentMatchId > 0L) {
					final var finalWinCondition = winCondition;

					tournamentMatch = loadTournamentMatch(account, currentMatchId)
						.flatMap(match -> {
							var otherTeam = allTeams.stream()
								.flatMap(Collection::stream)
								.filter(t -> !t.getId().equals(teamIdOfPlayer.get()))
								.filter(t -> t.getId().equals(match.getTeamOne().getId()) || t.getId().equals(match.getTeamTwo().getId()))
								.findFirst();

							return otherTeam.flatMap(sendouTournamentTeam -> mapToActiveMatch(account, tournament, teamOfPlayer.get(), sendouTournamentTeam, match, finalWinCondition));
						});
				}
			}
		}

		if (tournamentMatch.isPresent()) {
			return tournamentMatch;
		}

		return Optional.of(searchSendouQ)
			.filter(b -> b)
			.flatMap(ignored -> loadActiveSendouQMatchId(account, sendouAccountId))
			.flatMap(matchId -> loadSendouQMatch(account, matchId.getMatchId()))
			.flatMap(match -> mapToActiveMatch(account, sendouAccountId, match));
	}

	public Optional<SendouApiTournament> loadTournament(Account account, Long tournamentId) {
		var tournament = this.generateRequest(account, SendouApiTournament.class, "/api/tournament/%d", tournamentId);

		if (tournament == null || !tournament.isRunning()) {
			return Optional.empty();
		}

		return Optional.of(tournament);
	}

	public Optional<List<SendouTournamentTeam>> loadTournamentTeams(Account account, Long tournamentId) {
		var tournamentTeams = this.generateRequest(account, new TypeReference<List<SendouTournamentTeam>>() {
		}, "/api/tournament/%d/teams", tournamentId);

		if (tournamentTeams == null || tournamentTeams.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(tournamentTeams);
	}

	public Optional<SendouTournamentBracket> loadTournamentBracket(Account account, Long tournamentId, int bracketNumber) {
		var tournamentBracket = this.generateRequest(account, SendouTournamentBracket.class, "/api/tournament/%d/brackets/%d", tournamentId, bracketNumber);

		if (tournamentBracket == null) {
			return Optional.empty();
		}

		return Optional.of(tournamentBracket);
	}

	public Optional<SendouTournamentMatch> loadTournamentMatch(Account account, Long matchId) {
		var tournamentBracket = this.generateRequest(account, SendouTournamentMatch.class, "/api/tournament-match/%d", matchId);

		if (tournamentBracket == null) {
			return Optional.empty();
		}

		return Optional.of(tournamentBracket);
	}

	public Optional<MatchId> loadActiveSendouQMatchId(Account account, Long sendouAccountId) {
		var matchIdResponse = this.generateRequest(account, MatchId.class, "/api/sendouq/active-match/%d", sendouAccountId);

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

		sendouQMatchResponse.setMatchId(matchId);

		return Optional.of(sendouQMatchResponse);
	}

	public Optional<SendouMatch> mapToActiveMatch(@NonNull Account account, SendouApiTournament tournament, @NonNull SendouTournamentTeam loadedOwnTeam, @NonNull SendouTournamentTeam loadedOtherTeam, @NonNull SendouTournamentMatch tournamentMatch, @NonNull String winCondition) {
		var ownTeamIsOne = Objects.equals(loadedOwnTeam.getId(), tournamentMatch.getTeamOne().getId());

		var ownTeam = ownTeamIsOne ? tournamentMatch.getTeamOne() : tournamentMatch.getTeamTwo();
		var otherTeam = ownTeamIsOne ? tournamentMatch.getTeamTwo() : tournamentMatch.getTeamOne();

		var ownSendouTeam = SendouTeam.builder()
			.name(loadedOwnTeam.getName())
			.logoUrl(getAboluteUrl(loadedOwnTeam.getLogoUrl()))
			.players(loadedOwnTeam.getMembers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(p.getUserId().equals(account.getSendouId()))
					.build())
				.collect(Collectors.toList()))
			.build();

		var opponentSendouTeam = SendouTeam.builder()
			.name(loadedOtherTeam.getName())
			.logoUrl(getAboluteUrl(loadedOtherTeam.getLogoUrl()))
			.players(loadedOtherTeam.getMembers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(false)
					.build())
				.collect(Collectors.toList()))
			.build();

		var mapModes = tournamentMatch.getMapList().stream()
			.map(m -> MapMode.builder()
				.mode(MatchMode.fromString(m.getMap().getMode()))
				.stage(SendouMap.builder().id(m.getMap().getStage().getId()).name(m.getMap().getStage().getName()).build())
				.finished(m.getWinnerTeamId() != null)
				.ownTeamWon(ownTeam.getId().equals(m.getWinnerTeamId()))
				.ownScore(m.getPoints() != null && m.getPoints().size() >= 2 ? ownTeamIsOne ? m.getPoints().get(0) : m.getPoints().get(1) : null)
				.ownTeamPlayers(m.getParticipatedUserIds() != null
					? ownSendouTeam.getPlayers().stream().filter(p -> m.getParticipatedUserIds().contains(p.getId())).collect(Collectors.toList())
					: ownSendouTeam.getPlayers())
				.opponentScore((m.getPoints() != null && m.getPoints().size() >= 2 ? ownTeamIsOne ? m.getPoints().get(1) : m.getPoints().get(0) : null))
				.opponentTeamPlayers(m.getParticipatedUserIds() != null
					? opponentSendouTeam.getPlayers().stream().filter(p -> m.getParticipatedUserIds().contains(p.getId())).collect(Collectors.toList())
					: opponentSendouTeam.getPlayers())
				.pickReason(PickReason.determine(m.getSource(), tournamentMatch.getTeamOne().getId()))
				.build())
			.collect(Collectors.toList());

		var result = SendouMatch.builder()
			.matchActive(true)
			.url(tournamentMatch.getUrl())
			.type(MatchType.TOURNAMENT)
			.ownScore(ownTeam.getScore())
			.opponentScore(otherTeam.getScore())
			.myself(ownSendouTeam.getPlayers().stream().filter(SendouPlayer::isMyself).findFirst().orElseThrow())
			.ownTeam(ownSendouTeam)
			.opponentTeam(opponentSendouTeam)
			.mapModes(mapModes)
			.tournamentName(tournament.getName())
			.tournamentImageUrl(getAboluteUrl(tournament.getLogoUrl()))
			.bracketName(tournamentMatch.getBracketName())
			.roundName(tournamentMatch.getRoundName())
			.winCondition(winCondition)
			.build();

		return Optional.of(result);
	}

	private String getAboluteUrl(String url) {
		url = Optional.ofNullable(url).orElse("");

		try {
			if (!url.isBlank() && !new URI(url).isAbsolute()) {
				return String.format("https://sendou.nyc3.cdn.digitaloceanspaces.com/%s", url);
			}
		} catch (Exception ignored) {
		}
		return url;
	}

	public Optional<SendouMatch> mapToActiveMatch(@NonNull Account account, @NonNull Long playerId, @NonNull SendouQMatch sendouQMatch) {
		var ownTeamIsAlpha = sendouQMatch.getTeamAlpha().getPlayers().stream()
			.anyMatch(p -> playerId.equals(p.getUserId()));

		var ownTeam = ownTeamIsAlpha ? sendouQMatch.getTeamAlpha() : sendouQMatch.getTeamBravo();
		var otherTeam = ownTeamIsAlpha ? sendouQMatch.getTeamBravo() : sendouQMatch.getTeamAlpha();

		var ownSendouTeam = SendouTeam.builder()
			.name("Our Team")
			.logoUrl(null)
			.players(ownTeam.getPlayers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(p.getUserId().equals(account.getSendouId()))
					.build())
				.collect(Collectors.toList()))
			.build();

		var opponentSendouTeam = SendouTeam.builder()
			.name("Opponents")
			.logoUrl(null)
			.players(otherTeam.getPlayers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(getSendouUser(account, p.getUserId()).getName())
					.isMyself(false)
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
			.url(String.format("https://sendou.ink/q/match/%d", sendouQMatch.getMatchId()))
			.type(MatchType.SENDOU_Q)
			.ownScore(ownTeam.getScore())
			.opponentScore(otherTeam.getScore())
			.myself(ownSendouTeam.getPlayers().stream().filter(SendouPlayer::isMyself).findFirst().orElseThrow())
			.ownTeam(ownSendouTeam)
			.opponentTeam(opponentSendouTeam)
			.mapModes(mapModes)
			.tournamentName("SendouQ")
			.tournamentImageUrl(null)
			.bracketName("")
			.roundName("")
			.winCondition("bo7")
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

	private void fillMatchIntoStreamStatistics() {
		if (twitchBotClient.getWentLiveTime() == null) {
			return;
		}

		accountRepository.findByIsMainAccount(true).stream()
			.findFirst()
			.flatMap(account -> loadActiveMatch(account, sendouAccountId != null ? sendouAccountId : account.getSendouId(), tournamentId, searchSendouQ))
			.ifPresent(streamStatistics::setSendouMatchResult);
	}

	private <T> T generateRequest(Account account, Class<T> classToConvert, String path, Object... params) {
		var typeRef = new TypeReference<T>() {
			@Override
			public Type getType() {
				return classToConvert;
			}
		};
		return generateRequest(account, typeRef, path, params);
	}

	private <T> T generateRequest(Account account, TypeReference<T> classToConvert, String path, Object... params) {
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
			.runnable(this::fillMatchIntoStreamStatistics)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

package tv.strohi.twitch.strohkoenigbot.sendou;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.sendou.model.in.*;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.*;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.MapMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.model.Cached;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class SendouService implements ScheduledService {
	public final static Duration DEFAULT_CACHE_DURATION = Duration.ofSeconds(35);

	private final static URI API_URL = URI.create("https://sendou.ink/");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final LogSender logSender;

	private final AccountRepository accountRepository;

	private final TwitchBotClient twitchBotClient;
	private final S3StreamStatistics streamStatistics;

	private final Map<String, Cached<String>> sendouApiCache = new HashMap<>();
	private final Set<Long> finishedTournaments = new HashSet<>();
	private final Set<String> finishedBrackets = new HashSet<>();
	private final Set<Long> finishedMatches = new HashSet<>();

	@Getter
	private final Map<Integer, Integer> responseCodes = new HashMap<>();

	@Setter
	private Long sendouUserId = null;
	@Setter
	private Long tournamentId = null;
	@Setter
	private boolean searchSendouQ = false;

	public Optional<SendouMatch> loadActiveMatch(Account account, @NonNull Long sendouUserId, Long tournamentId, boolean searchSendouQ) {
		var tournamentMatch = Optional.<SendouMatch>empty();

		if (tournamentId != null) {
			var foundTournament = loadTournament(account, tournamentId);

			if (foundTournament.isPresent()) {
				var tournament = foundTournament.get();

				var allTeams = loadTournamentTeams(account, tournamentId);

				var teamOfPlayer = allTeams
					.stream()
					.flatMap(l -> l.stream().filter(t -> t.getMembers().stream().anyMatch(p -> Objects.equals(p.getUserId(), sendouUserId))))
					.findFirst();

				var teamIdOfPlayer = teamOfPlayer.map(SendouTournamentTeam::getId);

				var winCondition = "";
				var currentMatchId = 0L;
				var currentMatchStartedAt = 0L;
				var bracketNumber = 0;
				while (tournament.hasStarted() && teamIdOfPlayer.isPresent() && bracketNumber < tournament.getBrackets().size()) {
					var teamId = teamIdOfPlayer.get();

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
						.filter(m ->
							SendouTournamentMatchStatus.READY.equals(m.getStatus()) || SendouTournamentMatchStatus.RUNNING.equals(m.getStatus()))
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

							return otherTeam.flatMap(sendouTournamentTeam -> mapToActiveMatch(account, tournament, sendouUserId, teamOfPlayer.get(), sendouTournamentTeam, match, finalWinCondition));
						});
				}
			}
		}

		if (tournamentMatch.isPresent()) {
			return tournamentMatch;
		}

		return Optional.of(searchSendouQ)
			.filter(b -> b)
			.flatMap(ignored -> loadActiveSendouQMatchId(account, sendouUserId))
			.flatMap(matchId -> loadSendouQMatch(account, matchId.getMatchId()))
			.flatMap(match -> mapToActiveMatch(account, sendouUserId, match));
	}

	public Optional<MatchId> loadActiveSendouQMatchId(Account account, Long sendouUserId) {
		var matchIdResponse = this.generateRequest(account, MatchId.class, "/api/sendouq/active-match/%d", sendouUserId);

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

	private Optional<SendouApiTournament> loadTournament(Account account, Long tournamentId) {
		var tournament = this.generateRequest(
			account,
			SendouApiTournament.class,
			finishedTournaments.contains(tournamentId)
				? Duration.ofHours(1)
				: Duration.ofMinutes(10),
			"/api/tournament/%d",
			tournamentId);

		if (tournament != null && tournament.getIsFinalized()) {
			finishedTournaments.add(tournamentId);
		}

		if (tournament == null || !tournament.isRunning()) {
			return Optional.empty();
		}

		return Optional.of(tournament);
	}

	private Optional<List<SendouTournamentTeam>> loadTournamentTeams(Account account, Long tournamentId) {
		// no extended caching because of possible subs
		var tournamentTeams = this.generateRequest(account, new TypeReference<List<SendouTournamentTeam>>() {
		}, "/api/tournament/%d/teams", tournamentId);

		if (tournamentTeams == null || tournamentTeams.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(tournamentTeams);
	}

	private Optional<SendouTournamentBracket> loadTournamentBracket(Account account, Long tournamentId, int bracketNumber) {
		var bracketKey = String.format("%d/%d", tournamentId, bracketNumber);
		var tournamentBracket = this.generateRequest(
			account,
			SendouTournamentBracket.class,
			finishedBrackets.contains(bracketKey)
				? Duration.ofHours(1)
				: DEFAULT_CACHE_DURATION,
			"/api/tournament/%d/brackets/%d",
			tournamentId,
			bracketNumber);

		if (tournamentBracket == null) {
			return Optional.empty();
		}

		if (tournamentBracket.getData().getMatch().stream().allMatch(this::isCompleted)) {
			finishedBrackets.add(bracketKey);
		}

		tournamentBracket.getData().getMatch().stream()
			.filter(m -> m.getStatus() == SendouTournamentMatchStatus.COMPLETED)
			.forEach(m -> finishedMatches.add(m.getId()));

		return Optional.of(tournamentBracket);
	}

	private boolean isCompleted(SendouTournamentBracket.SendouTournamentBracketDataMatch match) {
		return match.getStatus() == SendouTournamentMatchStatus.COMPLETED
			|| (match.getStatus() == SendouTournamentMatchStatus.LOCKED && (match.getOpponent1() == null || match.getOpponent2() == null));
	}

	private Optional<SendouTournamentMatch> loadTournamentMatch(Account account, Long matchId) {
		var tournamentMatch = this.generateRequest(
			account,
			SendouTournamentMatch.class,
			finishedMatches.contains(matchId)
				? Duration.ofHours(1)
				: DEFAULT_CACHE_DURATION,
			"/api/tournament-match/%d",
			matchId);

		if (tournamentMatch == null) {
			return Optional.empty();
		}

		return Optional.of(tournamentMatch);
	}

	private SendouUser loadSendouUser(Account account, Long playerId) {
		var userResponse = this.generateRequest(account, SendouUser.class, Duration.ofHours(1), "/api/user/%d", playerId);

		if (userResponse == null || userResponse.getId() == null) {
			// no user found
			return SendouUser.builder()
				.name("unknown Player")
				.build();
		}

		return userResponse;
	}

	public Optional<SendouMatch> mapToActiveMatch(@NonNull Account account, SendouApiTournament tournament, @NonNull Long playerId, @NonNull SendouTournamentTeam loadedOwnTeam, @NonNull SendouTournamentTeam loadedOtherTeam, @NonNull SendouTournamentMatch tournamentMatch, @NonNull String winCondition) {
		var ownTeamIsOne = Objects.equals(loadedOwnTeam.getId(), tournamentMatch.getTeamOne().getId());

		var ownTeam = ownTeamIsOne ? tournamentMatch.getTeamOne() : tournamentMatch.getTeamTwo();
		var otherTeam = ownTeamIsOne ? tournamentMatch.getTeamTwo() : tournamentMatch.getTeamOne();

		var ownSendouTeam = SendouTeam.builder()
			.name(loadedOwnTeam.getName())
			.logoUrl(getAboluteUrl(loadedOwnTeam.getLogoUrl()))
			.seed(loadedOwnTeam.getSeed())
			.players(loadedOwnTeam.getMembers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(loadSendouUser(account, p.getUserId()).getName())
					.myself(p.getUserId().equals(playerId))
					.build())
				.collect(Collectors.toList()))
			.build();

		var opponentSendouTeam = SendouTeam.builder()
			.name(loadedOtherTeam.getName())
			.logoUrl(getAboluteUrl(loadedOtherTeam.getLogoUrl()))
			.seed(loadedOtherTeam.getSeed())
			.players(loadedOtherTeam.getMembers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(loadSendouUser(account, p.getUserId()).getName())
					.myself(false)
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
			.myself(ownSendouTeam.getPlayers().stream()
				.filter(p -> p.isMyself())
				.findFirst()
				.orElse(null))
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
					.name(loadSendouUser(account, p.getUserId()).getName())
					.myself(p.getUserId().equals(playerId))
					.build())
				.collect(Collectors.toList()))
			.build();

		var opponentSendouTeam = SendouTeam.builder()
			.name("Opponents")
			.logoUrl(null)
			.players(otherTeam.getPlayers().stream().map(p -> SendouPlayer.builder()
					.id(p.getUserId())
					.name(loadSendouUser(account, p.getUserId()).getName())
					.myself(false)
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
			.myself(ownSendouTeam.getPlayers().stream()
				.filter(p -> p.isMyself())
				.findFirst()
				.orElse(null))
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

	private void fillMatchIntoStreamStatistics() {
		if (twitchBotClient.getWentLiveTime() == null) {
			return;
		}

		accountRepository.findByIsMainAccount(true).stream()
			.findFirst()
			.flatMap(account -> loadActiveMatch(account, sendouUserId != null ? sendouUserId : account.getSendouId(), tournamentId, searchSendouQ))
			.ifPresent(streamStatistics::setSendouMatchResult);
	}

	private <T> T generateRequest(Account account, Class<T> classToConvert, String path, Object... params) {
		return generateRequest(account, classToConvert, DEFAULT_CACHE_DURATION, path, params);
	}

	private <T> T generateRequest(Account account, Class<T> classToConvert, Duration cacheDuration, String path, Object... params) {
		var typeRef = new TypeReference<T>() {
			@Override
			public Type getType() {
				return classToConvert;
			}
		};
		return generateRequest(account, typeRef, cacheDuration, path, params);
	}

	private <T> T generateRequest(Account account, TypeReference<T> typeToConvert, String path, Object... params) {
		return generateRequest(account, typeToConvert, DEFAULT_CACHE_DURATION, path, params);
	}

	private <T> T generateRequest(Account account, TypeReference<T> typeToConvert, Duration cacheDuration, String path, Object... params) {
		try {
			var requestPath = String.format(path, params);
			if (!sendouApiCache.containsKey(requestPath) || Instant.now().isAfter(sendouApiCache.get(requestPath).getExpirationTime())) {
				var request = HttpRequest.newBuilder()
					.GET()
					.uri(API_URL.resolve(requestPath))
					.setHeader("Authorization", String.format("Bearer %s", account.getSendouApiToken()))
					.build();

				var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
				responseCodes.put(response.statusCode(), responseCodes.getOrDefault(response.statusCode(), 0) + 1);

				if (response.statusCode() >= 200 && response.statusCode() <= 300) {
					sendouApiCache.put(requestPath, new Cached<>(Instant.now().plus(cacheDuration), new String(response.body())));
				} else {
					return null;
				}
			}

			return Optional.ofNullable(sendouApiCache.getOrDefault(requestPath, null))
				.map(body -> {
					try {
						return objectMapper.readValue(body.getObject(), typeToConvert);
					} catch (JsonProcessingException e) {
						return null;
					}
				})
				.orElse(null);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void clearCache() {
		var outdatedKeys = sendouApiCache.entrySet().stream()
			.filter(es -> Instant.now().isAfter(es.getValue().getExpirationTime()))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		if (!outdatedKeys.isEmpty()) {
			logSender.sendLogs(log,
				"Found a total of **%d** cache entries to remove. **%d** items in the cache before clearing, **%d** items afterwards.",
				outdatedKeys.size(),
				sendouApiCache.size(),
				sendouApiCache.size() - outdatedKeys.size());
		}

		outdatedKeys.forEach(sendouApiCache::remove);
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
				.name("SendouService_loadSendouGames")
				.schedule(TickSchedule.getScheduleString(6)) // every 30 secs
				.runnable(this::fillMatchIntoStreamStatistics)
				.build(),
			ScheduleRequest.builder()
				.name("SendouService_clearCache")
				.schedule(TickSchedule.getScheduleString(TickSchedule.ofMinutes(10)))
				.runnable(this::clearCache)
				.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

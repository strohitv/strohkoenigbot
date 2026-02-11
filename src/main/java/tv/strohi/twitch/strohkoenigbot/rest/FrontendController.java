package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.SendouMatchSearchResult;
import tv.strohi.twitch.strohkoenigbot.sendou.SendouService;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.MatchType;
import tv.strohi.twitch.strohkoenigbot.sendou.model.out.SendouMatch;
import tv.strohi.twitch.strohkoenigbot.utils.model.Cached;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static tv.strohi.twitch.strohkoenigbot.sendou.SendouService.DEFAULT_CACHE_DURATION;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class FrontendController {
	private final SendouService sendouService;
	private final AccountRepository accountRepository;

	private final Map<String, Cached<SendouMatchSearchResult>> cache = new HashMap<>();

	@GetMapping("sendou/match/search")
	public SendouMatchSearchResult searchSendouMatch(@RequestParam(name = "tournament_id", defaultValue = "2978") Long tournamentId,
													 @RequestParam(name = "user_id", defaultValue = "6238") Long sendouUserId) {
		var cacheKey = String.format("%d/%d", tournamentId, sendouUserId);

		if (cache.containsKey(cacheKey) && Instant.now().isBefore(cache.get(cacheKey).getExpirationTime())) {
			return cache.get(cacheKey).getObject();
		}

		var matchModel = accountRepository.findByIsMainAccount(true)
			.stream()
			.findFirst()
			.flatMap(account -> sendouService.loadActiveMatch(account, sendouUserId, tournamentId, false))
			.map(this::map)
			.orElse(SendouMatchSearchResult.builder().type(MatchType.NONE.name()).build());

		cache.put(cacheKey, new Cached<>(Instant.now().plus(DEFAULT_CACHE_DURATION), matchModel));

		return matchModel;
	}

	private SendouMatchSearchResult map(SendouMatch sendouMatch) {
		return SendouMatchSearchResult.builder()
			.type(sendouMatch.getType().name())
			.matchUrl(sendouMatch.getUrl())
			.tournamentName(sendouMatch.getTournamentName())
			.tournamentImageUrl(sendouMatch.getTournamentImageUrl())
			.bracketName(sendouMatch.getBracketName())
			.roundName(sendouMatch.getRoundName())
			.winCondition(sendouMatch.getWinCondition())
			.ownTeamName(sendouMatch.getOwnTeam().getName())
			.ownTeamImageUrl(sendouMatch.getOwnTeam().getLogoUrl())
			.ownTeamSeed(sendouMatch.getOwnTeam().getSeed())
			.ownScore(sendouMatch.getOwnScore())
			.opponentTeamName(sendouMatch.getOpponentTeam().getName())
			.opponentTeamImageUrl(sendouMatch.getOpponentTeam().getLogoUrl())
			.opponentTeamSeed(sendouMatch.getOpponentTeam().getSeed())
			.opponentScore(sendouMatch.getOpponentScore())
			.build();
	}
}

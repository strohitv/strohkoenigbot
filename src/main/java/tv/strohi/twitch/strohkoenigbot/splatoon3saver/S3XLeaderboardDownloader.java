package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.XRankLeaderBoard;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3XLeaderboardDownloader {
	private final S3ApiQuerySender apiQuerySender;
	private final S3StreamStatistics streamStatistics;

	private final AccountRepository accountRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	public Map<String, Double> loadTop500MinPower() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		var resultMap = new HashMap<String, Double>();

		if (account.isPresent()) {
			var season1StartDate = LocalDateTime.of(2022, 9, 1, 1, 0, 0);
			int seasonNumber = 1;

			var thisMonthStartDate = LocalDateTime.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), 1, 0, 0, 0);

			while (season1StartDate.plusMonths((seasonNumber - 1) * 3L).isBefore(thisMonthStartDate)) {
				seasonNumber++;
			}

			var tentatekSeasonHash = Base64.getEncoder().encodeToString(String.format("XRankingSeason-a:%d", seasonNumber).getBytes(StandardCharsets.UTF_8));
//			var takorokaSeasonHash = Base64.getEncoder().encodeToString(String.format("XRankingSeason-p:%d", seasonNumber).getBytes(StandardCharsets.UTF_8));

			for (var leaderBoardKey : S3RequestKey.getXRankLeaderBoardQueries()) {
				var xResponseTtekZones = apiQuerySender.queryS3ApiPaged(account.get(), leaderBoardKey, tentatekSeasonHash, 5, 25, "NzU");

				try {
					var leaderBoardPage = objectMapper.readValue(xResponseTtekZones, XRankLeaderBoard.class);

					var power = Arrays.stream(chooseXRankingField(leaderBoardPage.getData().getNode()).getEdges())
						.filter(e -> e.getNode().getRank() == 500)
						.findFirst()
						.map(e -> e.getNode().getXPower())
						.orElseThrow();

					resultMap.put(chooseRuleId(leaderBoardKey.getQueryName()), power);
				} catch (Exception ex) {
					log.error("could not download top 500 x power for query '{}'", leaderBoardKey.getKey());
					log.error(ex);
				}
			}
			// NzU = 75
//			var xResponseTtekZones = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageZones, tentatekSeasonHash, 5, 25, "NzU");
//			var xResponseTtekTower = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageTower, tentatekSeasonHash, 5, 25, "NzU");
//			var xResponseTtekRainmaker = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageRainmaker, tentatekSeasonHash, 5, 25, "NzU");
//			var xResponseTtekClams = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageClams, tentatekSeasonHash, 5, 25, "NzU");
////			var xResponseTakoZones = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageZones, takorokaSeasonHash, 5, 25, "NzU");
////			var xResponseTakoTower = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageTower, takorokaSeasonHash, 5, 25, "NzU");
////			var xResponseTakoRainmaker = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageRainmaker, takorokaSeasonHash, 5, 25, "NzU");
////			var xResponseTakoClams = apiQuerySender.queryS3ApiPaged(account.get(), S3RequestKey.XRankLeaderboardNextPageClams, takorokaSeasonHash, 5, 25, "NzU");
//
//			try {
//				var xStats = objectMapper.readValue(xResponse, XRankStats.class);
//				var xPlayer = xStats.getData().getXRanking().getPlayer();
//
//				var zonesPower = xPlayer.getStatsAr() != null ? xPlayer.getStatsAr().getLastXPower() : null;
//				var towerPower = xPlayer.getStatsLf() != null ? xPlayer.getStatsLf().getLastXPower() : null;
//				var rainmakerPower = xPlayer.getStatsGl() != null ? xPlayer.getStatsGl().getLastXPower() : null;
//				var clamsPower = xPlayer.getStatsCl() != null ? xPlayer.getStatsCl().getLastXPower() : null;
//
//				streamStatistics.setCurrentXPowers(zonesPower, towerPower, rainmakerPower, clamsPower);
//			} catch (Exception ex) {
//				log.error("could not refresh x powers", ex);
//			}
//
//			return Map.of();
		}

		return resultMap;
	}

	private String chooseRuleId(String queryName) {
		if (queryName.toLowerCase(Locale.ROOT).contains("ar")) {
			return "ar";
		} else if (queryName.toLowerCase(Locale.ROOT).contains("lf")) {
			return "lf";
		} else if (queryName.toLowerCase(Locale.ROOT).contains("gl")) {
			return "gl";
		} else {
			return "cl";
		}
	}

	private XRankLeaderBoard.XRankingBoard chooseXRankingField(XRankLeaderBoard.XRankingSeason data) {
		return Stream.of(data.getXRankingAr(), data.getXRankingLf(), data.getXRankingGl(), data.getXRankingCl())
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}
}

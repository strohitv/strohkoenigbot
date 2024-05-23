package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.XRankStats;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3XPowerDownloader {
	private final S3ApiQuerySender apiQuerySender;
	private final S3StreamStatistics streamStatistics;

	private final AccountRepository accountRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	public void fillXPower() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var xResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.XRankStats);

			try {
				var xStats = objectMapper.readValue(xResponse, XRankStats.class);
				var xPlayer = xStats.getData().getXRanking().getPlayer();

				var zonesPower = xPlayer.getStatsAr() != null ? xPlayer.getStatsAr().getLastXPower() : null;
				var towerPower = xPlayer.getStatsLf() != null ? xPlayer.getStatsLf().getLastXPower() : null;
				var rainmakerPower = xPlayer.getStatsGl() != null ? xPlayer.getStatsGl().getLastXPower() : null;
				var clamsPower = xPlayer.getStatsCl() != null ? xPlayer.getStatsCl().getLastXPower() : null;

				streamStatistics.setCurrentXPowers(zonesPower, towerPower, rainmakerPower, clamsPower);
			} catch (Exception ex) {
				log.error("could not refresh x powers", ex);
			}
		}
	}
}

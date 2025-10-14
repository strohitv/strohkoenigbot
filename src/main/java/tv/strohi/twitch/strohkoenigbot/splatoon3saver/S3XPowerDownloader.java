package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.XRankStats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3XPowerDownloader {
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final S3ApiQuerySender apiQuerySender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;

	public Optional<Powers> downloadXPowers() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var xResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.XRankStats);

			try {
				var xStats = objectMapper.readValue(xResponse, XRankStats.class);
				var xPlayer = xStats.getData().getXRanking().getPlayer();

				var zonesPower = xPlayer.getStatsAr() != null && xPlayer.getStatsAr().getLastXPower() >= 500 ? xPlayer.getStatsAr().getLastXPower() : null;
				var towerPower = xPlayer.getStatsLf() != null && xPlayer.getStatsLf().getLastXPower() >= 500 ? xPlayer.getStatsLf().getLastXPower() : null;
				var rainmakerPower = xPlayer.getStatsGl() != null && xPlayer.getStatsGl().getLastXPower() >= 500 ? xPlayer.getStatsGl().getLastXPower() : null;
				var clamsPower = xPlayer.getStatsCl() != null && xPlayer.getStatsCl().getLastXPower() >= 500 ? xPlayer.getStatsCl().getLastXPower() : null;

				return Optional.of(new Powers(zonesPower, towerPower, rainmakerPower, clamsPower));
			} catch (Exception ex) {
				exceptionLogger.logExceptionAsAttachment(log, "could not refresh x powers", ex);
			}
		}

		return Optional.empty();
	}

	@Getter
	@AllArgsConstructor
	public static class Powers {
		private final Double zones;
		private final Double tower;
		private final Double rainmaker;
		private final Double clams;
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.WeaponsResult;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3WeaponStatsDownloader {
	private final S3ApiQuerySender apiQuerySender;
	private final S3StreamStatistics streamStatistics;

	private final AccountRepository accountRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	public void fillWeaponStats() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var weaponsResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.Weapons);

			try {
				var weaponStats = objectMapper.readValue(weaponsResponse, WeaponsResult.class);
				var allWeapons = weaponStats.getData().getWeaponRecords().getNodes();

				streamStatistics.setCurrentWeaponRecords(allWeapons);
			} catch (Exception ex) {
				log.error("could not refresh weapon stats", ex);
			}
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.WeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3WeaponStatsDownloader {
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final S3ApiQuerySender apiQuerySender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;

	public Optional<Weapon[]> downloadWeaponStats() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var weaponsResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.Weapons);

			try {
				var weaponStats = objectMapper.readValue(weaponsResponse, WeaponsResult.class);
				var allWeapons = weaponStats.getData().getWeaponRecords().getNodes();

				return Optional.of(allWeapons);
			} catch (Exception ex) {
				exceptionLogger.logExceptionAsAttachment(log, "could not refresh weapon stats", ex);
			}
		}
		return Optional.empty();
	}
}

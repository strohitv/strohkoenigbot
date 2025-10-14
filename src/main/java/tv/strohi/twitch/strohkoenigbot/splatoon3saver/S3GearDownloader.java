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
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.OwnedGearAndWeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3GearDownloader {
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private final S3ApiQuerySender apiQuerySender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;

	public Optional<Gears> downloadGears() {
		var account = accountRepository.findByEnableSplatoon3(true).stream()
			.filter(Account::getIsMainAccount)
			.findFirst();

		if (account.isPresent()) {
			var gearResponse = apiQuerySender.queryS3Api(account.get(), S3RequestKey.OwnedWeaponsAndGear);

			try {
				var ownGearAndWeapons = objectMapper.readValue(gearResponse, OwnedGearAndWeaponsResult.class);

				var allHeadGears = ownGearAndWeapons.getData().getHeadGears().getNodes();
				var allClothingGears = ownGearAndWeapons.getData().getClothingGears().getNodes();
				var allShoesGears = ownGearAndWeapons.getData().getShoesGears().getNodes();

				return Optional.of(new Gears(allHeadGears, allClothingGears, allShoesGears));
			} catch (Exception ex) {
				exceptionLogger.logExceptionAsAttachment(log, "could not refresh gears", ex);
			}
		}

		return Optional.empty();
	}

	@Getter
	@AllArgsConstructor
	public static class Gears {
		private final Gear[] head;
		private final Gear[] clothing;
		private final Gear[] shoes;
	}
}

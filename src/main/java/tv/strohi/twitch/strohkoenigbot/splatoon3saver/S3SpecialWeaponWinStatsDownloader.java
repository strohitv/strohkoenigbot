package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3SpecialWeaponWinStatsDownloader {
	private final ExceptionLogger exceptionLogger;
	private final Splatoon3VsResultRepository resultRepository;

	public Optional<List<SpecialWinCount>> downloadSpecialWeaponStats() {
		try {
			var specialWeaponStats = resultRepository.findSpecialWins();
			return Optional.of(specialWeaponStats);
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "could not refresh special weapon stats", ex);
		}

		return Optional.empty();
	}
}

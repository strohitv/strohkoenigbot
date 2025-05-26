package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3SpecialWeaponWinStatsFiller {
	private final S3StreamStatistics streamStatistics;
	private final Splatoon3VsResultRepository resultRepository;

	public void fillSpecialWeaponStats() {
		try {
			streamStatistics.setCurrentSpecialWins(resultRepository.findSpecialWins());
		} catch (Exception ex) {
			log.error("could not refresh weapon stats", ex);
		}
	}
}

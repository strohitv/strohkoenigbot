package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3SpecialWeaponWinStatsFiller {
	private final S3StreamStatistics streamStatistics;
	private final Splatoon3VsResultRepository resultRepository;
	private final LogSender logSender;

	public void fillSpecialWeaponStats() {
		try {
			logSender.sendLogs(log, "filling special weapon stats...");
			streamStatistics.setCurrentSpecialWins(resultRepository.findSpecialWins());
		} catch (Exception ex) {
			logSender.sendLogs(log, "could not refresh weapon stats");
			log.error("could not refresh weapon stats", ex);
		}
	}
}

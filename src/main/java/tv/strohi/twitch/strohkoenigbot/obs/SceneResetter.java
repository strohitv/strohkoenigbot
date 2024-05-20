package tv.strohi.twitch.strohkoenigbot.obs;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.util.List;

@Component
@Log4j2
@RequiredArgsConstructor
public class SceneResetter implements ScheduledService {
	private final ObsController obsController;

	private int counter = 0;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("SceneResetter_resetAudioScene")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyMinutes(1)))
			.runnable(this::resetSchedule)
			.build());
	}

	private void resetSchedule() {
		if (ObsController.isLive()) {
			counter = (counter + 1) % 15;

			if (counter == 0) {
				obsController.changeSourceEnabled("4k30-Audio", false, disableResult -> {
					log.info("Result of disabling audio scene: {}", disableResult);

					if (disableResult) {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException ignored) {
						}

						obsController.changeSourceEnabled("4k30-Audio", true, enableResult -> log.info("Result of enabling audio scene: {}", enableResult));
					} else {
						// retry in a minute
						counter -= 1;
					}
				});
			}
		} else {
			// reset counter to make sure it doesn't try to control obs during inactive periods
			counter = 0;
		}
	}
}

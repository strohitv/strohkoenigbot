package tv.strohi.twitch.strohkoenigbot.utils.scheduling;

import org.springframework.stereotype.Component;

import javax.transaction.Transactional;

@Component
public class TransactionalScheduleRunner {
	@Transactional
	public void run(Runnable runnable) {
		runnable.run();
	}
}

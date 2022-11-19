package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import java.time.LocalDateTime;

public interface Schedule {
	boolean shouldRun(LocalDateTime time);
	Runnable getRunnable();

	void increaseErrorCount();
	boolean isFailed(int maxAttempts);
}

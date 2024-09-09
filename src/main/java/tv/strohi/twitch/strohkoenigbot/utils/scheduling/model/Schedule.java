package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import java.time.LocalDateTime;
import java.util.List;

public interface Schedule {
	boolean shouldRun(LocalDateTime time);

	Runnable getRunnable();

	Runnable getErrorCleanUpRunnable();

	String getName();

	List<Exception> getErrors();

	void run();

	void increaseErrorCount();

	boolean isFailed(int maxAttempts);
}

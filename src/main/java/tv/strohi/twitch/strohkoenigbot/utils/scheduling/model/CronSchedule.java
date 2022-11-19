package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import lombok.ToString;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

@ToString
public class CronSchedule implements Schedule {
	private final CronExpression cronExpression;
	private final Runnable runnable;

	private int errorCount = 0;

	public CronSchedule(String cron, Runnable runnable) {
		this.cronExpression = CronExpression.parse(cron);
		this.runnable = runnable;
	}

	private LocalDateTime lastFired = LocalDateTime.now();

	public static String getScheduleString(String cron) {
		return String.format("cron: %s", cron);
	}

	@Override
	public boolean shouldRun(LocalDateTime time) {
		LocalDateTime nextFireTime = null;

		try {
			nextFireTime = cronExpression.next(lastFired);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		boolean shouldRun = nextFireTime == null || nextFireTime.isBefore(time);

		if (shouldRun) {
			lastFired = time;
		}

		return shouldRun;
	}

	@Override
	public Runnable getRunnable() {
		return runnable;
	}

	@Override
	public void increaseErrorCount() {
		errorCount++;
	}

	@Override
	public boolean isFailed(int maxAttempts) {
		return errorCount >= maxAttempts;
	}
}

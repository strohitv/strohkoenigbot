package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ToString
public class CronSchedule implements Schedule {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final String name;
	private final CronExpression cronExpression;
	private final Runnable runnable;

	private int errorCount = 0;
	private final List<Exception> exceptions = new ArrayList<>();

	public CronSchedule(String name, String cron, Runnable runnable) {
		this.name = name;
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
			logger.error(ex.getMessage());
			logger.error(ex);
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
	public String getName() {
		return name;
	}

	@Override
	public List<Exception> getErrors() {
		return List.copyOf(exceptions);
	}

	@Override
	public void run() {
		try {
			runnable.run();
		} catch (Exception ex) {
			logger.error(ex.getMessage());
			logger.error(ex);

			increaseErrorCount();
			exceptions.add(ex);
		}
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

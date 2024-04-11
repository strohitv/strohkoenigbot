package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@ToString
public class TickSchedule implements Schedule {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final String name;
	private final int runEveryTicks;
	private final Runnable runnable;

	private int errorCount = 0;
	private final List<Exception> exceptions = new ArrayList<>();

	private int currentTick = 0;

	public static int everyMinutes(int minutes) {
		return minutes * 12;
	}

	public static int everyHours(int hours) {
		return hours * 60 * 12;
	}

	public static String getScheduleString(int ticks) {
		return String.format("tick: %d", ticks);
	}

	@Override
	public boolean shouldRun(LocalDateTime time) {
		currentTick++;
		return currentTick % runEveryTicks == 0;
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

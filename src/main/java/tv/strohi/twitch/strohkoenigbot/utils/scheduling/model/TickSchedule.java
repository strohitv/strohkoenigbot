package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@ToString
public class TickSchedule implements Schedule {
	private final int runEveryTicks;
	private final Runnable runnable;

	private int errorCount = 0;

	private int currentTick = 0;

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
	public void increaseErrorCount() {
		errorCount++;
	}

	@Override
	public boolean isFailed(int maxAttempts) {
		return errorCount >= maxAttempts;
	}
}

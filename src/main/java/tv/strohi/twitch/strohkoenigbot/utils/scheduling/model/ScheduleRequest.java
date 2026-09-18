package tv.strohi.twitch.strohkoenigbot.utils.scheduling.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequest {
	private String name;
	private String schedule;
	private Runnable runnable;
	private Runnable errorCleanUpRunnable;

	@Builder.Default
	private boolean prioritized = false;
}

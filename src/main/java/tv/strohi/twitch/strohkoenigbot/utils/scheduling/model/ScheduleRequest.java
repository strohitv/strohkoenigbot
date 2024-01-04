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
}

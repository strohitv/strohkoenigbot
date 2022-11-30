package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CoopRotation {
	private String startTime;
	private String endTime;

	private CoopSetting setting;

	public Instant getStartTimeAsInstant() {
		return Instant.parse(startTime);
	}

	public Instant getEndTimeAsInstant() {
		return Instant.parse(endTime);
	}
}

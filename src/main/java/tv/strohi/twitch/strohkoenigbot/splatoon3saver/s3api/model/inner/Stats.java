package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class Stats {
	private Integer exp;
	private Long paint;

	// stage stats
	private Double winRateTw;
	private Double winRateAr;
	private Double winRateLf;
	private Double winRateGl;
	private Double winRateCl;

	private String lastUsedTime;
	private Integer level;
	private Integer expToLevelUp;
	private Integer win;
	private Double vibes;

	public Instant getLastUsedTimeAsInstant() {
		return Instant.parse(lastUsedTime);
	}
}

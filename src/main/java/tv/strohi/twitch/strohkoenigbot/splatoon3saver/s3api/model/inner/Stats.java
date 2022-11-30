package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Stats {
	private Integer exp;
	private Long paint;

	// stage stats
	private Double winRateAr;
	private Double winRateLf;
	private Double winRateGl;
	private Double winRateCl;
}

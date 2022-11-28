package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class GearOffer {
	private String id;
	private Integer price;
	private Gear gear;

	private String saleEndTime;

	public Instant getSaleEndTimeAsInstant() {
		return Instant.parse(saleEndTime);
	}
}

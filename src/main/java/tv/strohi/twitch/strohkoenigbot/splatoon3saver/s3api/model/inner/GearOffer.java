package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import com.fasterxml.jackson.annotation.JsonProperty;
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

	@JsonProperty("isAlreadyOrdered")
	private boolean isAlreadyOrdered;
	private Id nextGear;
	private Id previousGear;
	private Gear ownedGear;

	public Instant getSaleEndTimeAsInstant() {
		return Instant.parse(saleEndTime);
	}
}

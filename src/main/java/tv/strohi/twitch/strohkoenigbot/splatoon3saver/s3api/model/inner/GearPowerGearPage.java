package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class GearPowerGearPage implements Serializable {
	private String __typename;
	private Long gearPowerId;

	private String name;
	private Image image;

	private Double power;
}

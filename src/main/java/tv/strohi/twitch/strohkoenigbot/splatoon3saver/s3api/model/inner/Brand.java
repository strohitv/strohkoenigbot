package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class Brand implements Serializable {
	private String id;
	private String name;
	private Image image;
	private GearPower usualGearPower;

	// gear list
	private String __typename;
}

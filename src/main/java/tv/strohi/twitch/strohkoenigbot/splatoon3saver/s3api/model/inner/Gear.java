package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class Gear implements Serializable {
	private String __isGear;
	private String name;
	private Brand brand;

	private NameAndImage primaryGearPower;
	private List<NameAndImage> additionalGearPowers;

	private Image image;
	private Image originalImage;
	private Image thumbnailImage;
}

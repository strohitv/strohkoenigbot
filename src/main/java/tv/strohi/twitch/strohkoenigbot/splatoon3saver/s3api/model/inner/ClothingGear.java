package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class ClothingGear implements Serializable {
	private NameAndImage primaryGearPower;
	private List<NameAndImage> additionalGearPowers;
	private String name;
	private String __isGear;
	private Image thumbnailImage;
	private Image originalImage;
	private IdAndNameAndImage brand;
}

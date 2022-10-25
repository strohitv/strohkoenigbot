package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class SpecialWeapon implements Serializable {
	private Image image;
	private MaskingImage maskingImage;
	private String name;
	private String id;
}

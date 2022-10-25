package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class Weapon implements Serializable {
	private IdAndNameAndImage subWeapon;
	private Image image;
	private Image image3dThumbnail;
	private String name;
	private Image image2d;
	private SpecialWeapon specialWeapon;
	private Image image3d;
	private String id;
	private Image image2dThumbnail;
}

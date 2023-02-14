package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class WeaponDetail implements Serializable {
	private String __typename;
	private String id;
	private String name;

	private Image image;
	private MaskingImage maskingImage;

	private Integer subWeaponId;
	private Integer specialWeaponId;
}

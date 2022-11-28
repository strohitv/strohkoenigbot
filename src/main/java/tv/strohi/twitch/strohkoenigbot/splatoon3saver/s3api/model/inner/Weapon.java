package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class Weapon implements Serializable {
	private String id;

	private String name;
	private Image image;

	private Image image2d;
	private Image image2dThumbnail;
	private Image image3d;
	private Image image3dThumbnail;

	private WeaponDetail subWeapon;
	private WeaponDetail specialWeapon;

	// gear page
	private String __typename;
	private Long weaponId;
	private Stats stats;
	private WeaponCategory weaponCategory;
}

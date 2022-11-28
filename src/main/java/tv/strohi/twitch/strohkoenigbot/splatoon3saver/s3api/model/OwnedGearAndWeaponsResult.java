package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OwnedGearAndWeaponsResult implements Serializable {
	private OwnedGearAndWeaponsResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private WeaponNodes weapons;
		private GearNodes headGears;
		private GearNodes clothingGears;
		private GearNodes shoesGears;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class WeaponNodes implements Serializable {
		private Weapon[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class GearNodes implements Serializable {
		private Gear[] nodes;
	}
}

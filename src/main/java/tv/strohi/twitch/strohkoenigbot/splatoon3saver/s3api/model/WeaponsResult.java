package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeaponsResult implements Serializable {
	private WeaponsResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private WeaponNodes weaponRecords;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class WeaponNodes implements Serializable {
		private Weapon[] nodes;
	}
}

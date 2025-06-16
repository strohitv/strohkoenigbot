package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPlayer implements Serializable {
	private String __isPlayer;
	private String byname;
	private String name;
	private String nameId;

	private Nameplate nameplate;

	private Weapon weapon;

	private Gear headGear;
	private Gear clothingGear;
	private Gear shoesGear;

	private Image userIcon;

	private BestNine bestNine;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BestNine implements Serializable {
		private Double powerTotal;
		private WeaponPowerOrders weaponPowerOrders;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponPowerOrders implements Serializable {
		private List<WeaponPowerOrderNodes> nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponPowerOrderNodes implements Serializable {
		private String id;
		private Weapon weapon;
	}
}

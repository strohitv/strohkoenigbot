package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// @Accessors(fluent = true)
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

	private Nothing bestNine;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class Player implements Serializable {
	private String __typename;

	private String id;
	private String name;
	private String nameId;
	private String byname;

	private Integer paint;
	private Boolean isMyself;
	private PlayerResult result;
	private Weapon weapon;
	private String festDragonCert;
	private String species;
	private String __isPlayer;
	private Nameplate nameplate;

	private Gear headGear;
	private Gear clothingGear;
	private Gear shoesGear;

	private Boolean crown;

	// Splatfest
	private String festGrade;

	// Salmon Run
	private IdAndNameAndImage uniform;
}

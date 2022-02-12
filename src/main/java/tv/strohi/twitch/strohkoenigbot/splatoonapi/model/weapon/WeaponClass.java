package tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum WeaponClass {
	Shooter("Shooter"),
	Semi("Semi"),
	Blaster("Blaster"),
	Charger("Charger"),
	Roller("Roller"),
	Slosher("Slosher"),
	Brush("Brush"),
	Splatling("Splatling"),
	Dualies("Dualie"),
	Brella("Brella");

	private final String name;

	WeaponClass(String name) {
		this.name = name;
	}

	public static final EnumSet<WeaponClass> All = EnumSet.allOf(WeaponClass.class);
}

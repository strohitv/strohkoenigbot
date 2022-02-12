package tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon;

import lombok.Getter;

@Getter
public enum SpecialWeapon {
	TentaMissiles("Tenta Missiles"),
	StingRay("Sting Ray"),
	Inkjet("Inkjet"),
	Splashdown("Splashdown"),
	InkArmor("Ink Armor"),
	BombLauncher("Bomb Launcher"),
	InkStorm("Ink Storm"),
	Baller("Baller"),
	BubbleBlower("Bubble Blower"),
	BooyahBomb("Booyah Bomb"),
	UltraStamp("Ultra Stamp");

	private final String name;

	SpecialWeapon(String name) {
		this.name = name;
	}
}

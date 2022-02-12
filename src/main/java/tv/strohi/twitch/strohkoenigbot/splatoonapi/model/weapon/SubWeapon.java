package tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon;

import lombok.Getter;

@Getter
public enum SubWeapon {
	SplatBomb("Splat Bomb"),
	SuctionBomb("Suction Bomb"),
	BurstBomb("Burst Bomb"),
	CurlingBomb("Curling Bomb"),
	Autobomb("Autobomb"),
	InkMine("Ink Mine"),
	ToxicMist("Toxic Mist"),
	PointSensor("Point Sensor"),
	SplashWall("Splash Wall"),
	Sprinkler("Sprinkler"),
	SquidBeakon("Squid Beakon"),
	FizzyBomb("Fizzy Bomb"),
	Torpedo("Torpedo");

	private final String name;

	SubWeapon(String name) {
		this.name = name;
	}
}

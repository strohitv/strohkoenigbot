package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@AllArgsConstructor
@Getter
public enum S3RequestKey {
	Home("22e2fa8294168003c21b00c333c35384"),
	History("d997d8e3875d50d3a1dc7e8a756e9e07"),

	Latest("0176a47218d830ee447e10af4a287b3f"),
	Regular("3baef04b095ad8975ea679d722bc17de"),
	Anarchy("0438ea6978ae8bd77c5d1250f4f84803"),
	XRank("6796e3cd5dc3ebd51864dc709d899fc5"),
	Private("8e5ae78b194264a6c230e262d069bd28"),
	GameDetail("291295ad311b99a6288fc95a5c4cb2d2"),

	Salmon("91b917becd2fa415890f5b47e15ffb15"),
	SalmonDetail("379f0d9b78b531be53044bcac031b34b"),

	SplatNetShop("a43dd44899a09013bcfd29b4b13314ff"),
	OwnedWeaponsAndGear("d29cd0c2b5e6bac90dd5b817914832f8"),
	Weapons("5f279779e7081f2d14ae1ddca0db2b6e"),

	RotationSchedules("011e394c0e384d77a0701474c8c11a20");

	@Getter
	private final static EnumSet<S3RequestKey> onlineBattles = EnumSet.of(Regular, Anarchy, XRank, Private); // EnumSet.of(Latest, Regular, Anarchy, Private); //

	private final String key;
}

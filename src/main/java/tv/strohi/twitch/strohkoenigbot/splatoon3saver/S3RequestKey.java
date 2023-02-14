package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@AllArgsConstructor
@Getter
public enum S3RequestKey {
	Home("dba47124d5ec3090c97ba17db5d2f4b3"),

	Latest("4f5f26e64bca394b45345a65a2f383bd"),
	Regular("d5b795d09e67ce153e622a184b7e7dfa"),
	Anarchy("de4754588109b77dbcb90fbe44b612ee"),
	XRank("45c74fefb45a49073207229ca65f0a62"),
	Private("1d6ed57dc8b801863126ad4f351dfb9a"),
	GameDetail("291295ad311b99a6288fc95a5c4cb2d2"),

	Salmon("6ed02537e4a65bbb5e7f4f23092f6154"),
	SalmonDetail("3cc5f826a6646b85f3ae45db51bd0707"),

	SplatNetShop("a43dd44899a09013bcfd29b4b13314ff"),
	OwnedWeaponsAndGear("d29cd0c2b5e6bac90dd5b817914832f8"),
	Weapons("5f279779e7081f2d14ae1ddca0db2b6e"),

	RotationSchedules("730cd98e84f1030d3e9ac86b6f1aae13");

	@Getter
	private final static EnumSet<S3RequestKey> onlineBattles = EnumSet.of(Regular, Anarchy, XRank, Private); // EnumSet.of(Latest, Regular, Anarchy, Private); //

	private final String key;
}

package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.ToString;

@ToString
public enum MatchMode {
	TURF,
	ZONES,
	TOWER,
	RAIN,
	CLAMS;

	public static MatchMode fromString(String matchMode) {
		MatchMode mode;

		switch (matchMode) {
			case "TW":
				mode = TURF;
				break;
			case "RM":
				mode = RAIN;
				break;
			case "TC":
				mode = TOWER;
				break;
			case "CB":
				mode = CLAMS;
				break;
			case "SZ":
			default:
				mode = ZONES;
				break;
		}

		return mode;
	}
}

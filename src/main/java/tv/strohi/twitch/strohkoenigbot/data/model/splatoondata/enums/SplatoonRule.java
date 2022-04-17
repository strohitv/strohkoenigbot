package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums;

public enum SplatoonRule {
	TurfWar,
	SplatFestTurfWar,
	SplatZones,
	Rainmaker,
	TowerControl,
	ClamBlitz;

	public static SplatoonRule getRuleByName(String key) {
		SplatoonRule mode = SplatFestTurfWar;

		switch (key) {
			case "turf_war":
				mode = TurfWar;
				break;
			case "splat_zones":
				mode = SplatZones;
				break;
			case "rainmaker":
				mode = Rainmaker;
				break;
			case "tower_control":
				mode = TowerControl;
				break;
			case "clam_blitz":
				mode = ClamBlitz;
				break;
			default:
				break;
		}

		return mode;
	}

	public String getAsString() {
		String name = "Splatfest Turf War";

		switch (this) {
			case TurfWar:
				name = "Turf War";
				break;
			case SplatZones:
				name = "Splat Zones";
				break;
			case Rainmaker:
				name = "Rainmaker";
				break;
			case TowerControl:
				name = "Tower Control";
				break;
			case ClamBlitz:
				name = "Clam Blitz";
				break;
			default:
				break;
		}

		return name;
	}
}

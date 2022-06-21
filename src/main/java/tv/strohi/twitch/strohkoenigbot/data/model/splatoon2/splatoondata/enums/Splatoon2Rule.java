package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums;

public enum Splatoon2Rule {
	TurfWar,
	SplatFestTurfWar,
	SplatZones,
	Rainmaker,
	TowerControl,
	ClamBlitz;

	public static Splatoon2Rule getRuleByName(String key) {
		Splatoon2Rule mode = SplatFestTurfWar;

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

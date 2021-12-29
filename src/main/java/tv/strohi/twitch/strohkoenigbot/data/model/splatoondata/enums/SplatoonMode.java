package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums;

public enum SplatoonMode {
	TurfWar,
	SplatFestTurfWar,
	Ranked,
	League,
	PrivateBattle;

	public static SplatoonMode getModeByName(String key) {
		SplatoonMode mode = SplatFestTurfWar;

		switch (key) {
			case "regular":
				mode = TurfWar;
				break;
			case "gachi":
				mode = Ranked;
				break;
			case "league":
			case "league_pair":
			case "league_team":
				mode = League;
				break;
			case "private":
				mode = PrivateBattle;
				break;
			default:
				break;
		}

		return mode;
	}
}

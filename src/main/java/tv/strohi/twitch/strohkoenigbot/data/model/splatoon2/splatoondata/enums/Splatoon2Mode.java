package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums;

public enum Splatoon2Mode {
	TurfWar,
	SplatFestTurfWar,
	Ranked,
	League,
	PrivateBattle;

	public static Splatoon2Mode getModeByName(String key) {
		Splatoon2Mode mode = SplatFestTurfWar;

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

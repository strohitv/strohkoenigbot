package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums;

public enum Splatoon2MatchResult {
	Win,
	Defeat,
	Draw;

	public static Splatoon2MatchResult parseResult(String resultStr) {
		Splatoon2MatchResult result;

		switch (resultStr) {
			case "victory":
				result = Win;
				break;
			case "defeat":
				result = Defeat;
				break;
			default:
				result = Draw;
				break;
		}

		return result;
	}
}

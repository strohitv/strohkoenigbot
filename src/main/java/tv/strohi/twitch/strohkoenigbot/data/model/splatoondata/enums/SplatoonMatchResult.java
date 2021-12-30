package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums;

public enum SplatoonMatchResult {
	Win,
	Defeat,
	Draw;

	public static SplatoonMatchResult parseResult(String resultStr) {
		SplatoonMatchResult result;

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

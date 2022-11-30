package tv.strohi.twitch.strohkoenigbot.utils;

public class DiscordChannelDecisionMaker {
	private final static String debugTempChannel = "debug-logs-temp";

	private final static String debugChannel = "debug-logs";
	private final static String s2SplatNetGearChannel = "s2-splatnet-gear";
	private final static String s2TurfWarChannel = "s2-turf-war-rotations";
	private final static String s2RankedChannel = "s2-ranked-rotations";
	private final static String s2LeagueChannel = "s2-league-rotations";
	private final static String s2SalmonRunChannel = "s2-salmon-run-rotations";
	private final static String s2MatchChannel = "s2-matches";

	private static boolean isLocalDebug = false;

	public static boolean isIsLocalDebug() {
		return isLocalDebug;
	}

	public static void setIsLocalDebug(boolean isLocalDebug) {
		DiscordChannelDecisionMaker.isLocalDebug = isLocalDebug;
	}

	private DiscordChannelDecisionMaker () {}

	public static String getDebugChannelName() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return debugChannel;
		}
	}

	public static String getS2SplatNetGearChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2SplatNetGearChannel;
		}
	}

	public static String getS2TurfWarChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2TurfWarChannel;
		}
	}

	public static String getS2RankedChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2RankedChannel;
		}
	}

	public static String getS2LeagueChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2LeagueChannel;
		}
	}

	public static String getS2SalmonRunChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2SalmonRunChannel;
		}
	}

	public static String getS2MatchChannelName() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s2MatchChannel;
		}
	}
}

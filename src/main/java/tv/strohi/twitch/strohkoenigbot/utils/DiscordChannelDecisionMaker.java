package tv.strohi.twitch.strohkoenigbot.utils;

public class DiscordChannelDecisionMaker {
	private final static String debugTempChannel = "debug-logs-temp";

	private final static String debugChannel = "debug-logs";
	private final static String splatNetGearChannel = "splatnet-gear";
	private final static String turfWarChannel = "turf-war-rotations";
	private final static String rankedChannel = "ranked-rotations";
	private final static String leagueChannel = "league-rotations";
	private final static String salmonRunChannel = "salmon-run-rotations";

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

	public static String getSplatNetGearChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return splatNetGearChannel;
		}
	}

	public static String getTurfWarChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return turfWarChannel;
		}
	}

	public static String getRankedChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return rankedChannel;
		}
	}

	public static String getLeagueChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return leagueChannel;
		}
	}

	public static String getSalmonRunChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return salmonRunChannel;
		}
	}
}

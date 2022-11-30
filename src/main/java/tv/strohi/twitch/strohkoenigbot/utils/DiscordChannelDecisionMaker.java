package tv.strohi.twitch.strohkoenigbot.utils;

public class DiscordChannelDecisionMaker {
	private final static String debugTempChannel = "debug-logs-temp";

	private final static String debugChannel = "debug-logs";

	// Splatoon 2
	private final static String s2SplatNetGearChannel = "s2-splatnet-gear";
	private final static String s2TurfWarChannel = "s2-turf-war-rotations";
	private final static String s2RankedChannel = "s2-ranked-rotations";
	private final static String s2LeagueChannel = "s2-league-rotations";
	private final static String s2SalmonRunChannel = "s2-salmon-run-rotations";
	private final static String s2MatchChannel = "s2-matches";

	// Splatoon 3
	private final static String s3SplatNetGearChannel = "s3-splatnet-gear";
	private final static String s3SplatNetDailyGearChannel = "s3-splatnet-daily";
	private final static String s3TurfWarChannel = "s3-turf-war-rotations";
	private final static String s3SplatfestChannel = "s3-splatfest-rotations";
	private final static String s3AnarchySeriesChannel = "s3-anarchy-series-rotations";
	private final static String s3AnarchyOpenChannel = "s3-anarchy-open-rotations";
	private final static String s3XRankChannel = "s3-x-rank-rotations";
	private final static String s3LeagueChannel = "s3-league-rotations";
	private final static String s3SalmonRunChannel = "s3-salmon-run-rotations";
	private final static String s3SalmonRunBigRunChannel = "s3-big-run-rotations";

	private static boolean isLocalDebug = false;

	public static boolean isIsLocalDebug() {
		return isLocalDebug;
	}

	public static void setIsLocalDebug(boolean isLocalDebug) {
		DiscordChannelDecisionMaker.isLocalDebug = isLocalDebug;
	}

	private DiscordChannelDecisionMaker() {
	}

	public static String getDebugChannelName() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return debugChannel;
		}
	}

	// Splatoon 2
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

	// Splatoon 3
	public static String getS3SplatNetGearChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3SplatNetGearChannel;
		}
	}

	public static String getS3SplatNetDailyGearChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3SplatNetDailyGearChannel;
		}
	}

	public static String getS3TurfWarChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3TurfWarChannel;
		}
	}

	public static String getS3SplatfestChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3SplatfestChannel;
		}
	}

	public static String getS3AnarchySeriesChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3AnarchySeriesChannel;
		}
	}

	public static String getS3AnarchyOpenChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3AnarchyOpenChannel;
		}
	}

	public static String getS3XRankChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3XRankChannel;
		}
	}

	public static String getS3LeagueChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3LeagueChannel;
		}
	}

	public static String getS3SalmonRunChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3SalmonRunChannel;
		}
	}

	public static String getS3SalmonRunBigRunChannel() {
		if (isLocalDebug) {
			return debugTempChannel;
		} else {
			return s3SalmonRunBigRunChannel;
		}
	}
}

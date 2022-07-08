package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;

import java.util.Locale;

public class MatchMessageFormatter {
	public static String getMatchResultMessage(Splatoon2Match match, SplatNetMatchResult singleResult) {
		return String.format(
				"**I finished a Splatoon 2 match!**\n" +
						"\n" +
						"**General results**:\n" +
						"- Account Id: **%d**\n" +
						"- Mode: **%s**\n" +
						"- Rule: **%s**\n" +
						"- It was a **%s**\n" +
						"- My weapon: **%s**\n" +
						"- Our score: **%s**\n" +
						"- Enemy score: **%s**\n" +
						"\n" +
						"**Personal results**:\n" +
						"- Splats: **%d**\n" +
						"- Assists: **%d**\n" +
						"- Deaths: **%d**\n" +
						"- Paint: + **%d** points\n\n" +
						"---------------------------------",
				match.getAccountId(),
				match.getMode(),
				match.getRule(),
				match.getMatchResult(),
				singleResult.getPlayer_result().getPlayer().getWeapon().getName(),
				match.getOwnPercentage() != null ? match.getOwnPercentage() : match.getOwnScore(),
				match.getEnemyPercentage() != null ? match.getEnemyPercentage() : match.getEnemyScore(),

				match.getKills(),
				match.getAssists(),
				match.getDeaths(),
				match.getTurfGain());
	}

	public static String getMatchResultPerformance(Splatoon2Match match) {
		return String.format("Last match: %s (%s : %s %s) - own stats: %dp ink, %d kills, %d assists, %d specials, %d deaths",
				match.getRule().getAsString(),
				match.getOwnScore() != null ? String.format("%d", match.getOwnScore()) : String.format(Locale.US, "%.1f%%", match.getOwnPercentage()),
				match.getEnemyScore() != null ? String.format("%d", match.getEnemyScore()) : String.format(Locale.US, "%.1f%%", match.getEnemyPercentage()),
				match.getMatchResult() == Splatoon2MatchResult.Win ? "win" : "defeat",
				match.getTurfGain(),
				match.getKills(),
				match.getAssists(),
				match.getSpecials(),
				match.getDeaths());
	}

	public static String getAddedMatchMessage(Splatoon2Match match) {
		return String.format("Put new Match with id **%d** for account **%d** for mode **%s** and rule **%s** into database. It was a **%s**.",
				match.getId(),
				match.getAccountId(),
				match.getMode(),
				match.getRule(),
				match.getMatchResult());
	}

	public static String getCurrentPowerMessage(String rule, int year, int month, double current) {
		return String.format("Current %s power for month **%d-%d** is now **%.1f**.", rule, year, month, current);
	}

	public static String getPeakPowerMessage(String rule, int year, int month, double peak) {
		return String.format("%s peak for month **%d-%d** is now **%.1f**.", rule, year, month, peak);
	}
}

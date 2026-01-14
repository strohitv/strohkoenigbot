package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.ToString;

@ToString
public enum PickReason {
	TEAM_ALPHA,
	TEAM_BRAVO,
	TIEBREAKER,
	DEFAULT,
	BOTH,
	TO,
	COUNTERPICK;

	public static PickReason determine(Object reason, Long teamAlphaId) {
		PickReason pickReason;

		if (reason instanceof Number) {
			Long reasonLong;

			if (reason instanceof Long) {
				reasonLong = (Long) reason;
			} else {
				reasonLong = Long.valueOf((Integer) reason);
			}

			if (teamAlphaId.equals(reasonLong)) {
				pickReason = PickReason.TEAM_ALPHA;
			} else {
				pickReason = PickReason.TEAM_BRAVO;
			}
		} else {
			var reasonStr = (String) reason;

			if ("DEFAULT".equals(reasonStr)) {
				pickReason = PickReason.DEFAULT;
			} else if ("TIEBREAKER".equals(reasonStr)) {
				pickReason = PickReason.TIEBREAKER;
			} else if ("BOTH".equals(reasonStr)) {
				pickReason = PickReason.BOTH;
			} else if ("TO".equals(reasonStr)) {
				pickReason = PickReason.TO;
			} else if ("COUNTERPICK".equals(reasonStr)) {
				pickReason = PickReason.COUNTERPICK;
			} else {
				throw new IllegalArgumentException("Unknown reason: " + reason);
			}
		}

		return pickReason;
	}
}

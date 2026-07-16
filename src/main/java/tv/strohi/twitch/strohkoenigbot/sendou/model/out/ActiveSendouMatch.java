package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class ActiveSendouMatch {
	/** The user's current match ID or null if none */
	private Long matchId;
	/** What kind of match the user is in right now */
	private String lobby;
	/** The ID of the tournament (null for sendouq or no match) */
	private Long tournamentId;
	/** The bracket index within the tournament (null for sendouq or no match). Can be used with GET /api/tournament/{tournamentId}/brackets/{bracketIdx} */
	private Integer bracketIdx;

	public MatchType getLobbyAsEnum() {
		if (lobby != null) {
			switch (lobby) {
				case "sendouq":
					return MatchType.SENDOU_Q;
				case "tournament":
					return MatchType.TOURNAMENT;
				default:
					throw new IllegalArgumentException(String.format("`%s` is no known sendou lobby!", lobby));
			}
		}

		return MatchType.NONE;
	}
}

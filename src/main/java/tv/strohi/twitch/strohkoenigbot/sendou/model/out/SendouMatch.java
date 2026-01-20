package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class SendouMatch {
	private Long matchId;

	private boolean matchActive;

	private MatchType type;

	private int ownScore;
	private int opponentScore;

	private SendouPlayer myself;

	@NonNull
	private SendouTeam ownTeam;
	@NonNull
	private SendouTeam opponentTeam;

	private List<MapMode> mapModes;

//	@Override
//	public boolean equals(Object obj) {
//		if (!(obj instanceof SendouMatch)) {
//			return false;
//		}
//
//		var otherMatch = (SendouMatch) obj;
//		return ownTeam.getId() == otherMatch.ownTeam.getId()
//			&& opponentTeam.getId() == otherMatch.opponentTeam.getId()
//			&& type == otherMatch.type
//			&& ownScore == otherMatch.ownScore
//			&& opponentScore == otherMatch.opponentScore;
//	}
}

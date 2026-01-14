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
public class MapMode {
	private MatchMode mode;
	private SendouMap stage;
	private PickReason pickReason;

	private boolean finished;
	private Boolean ownTeamWon;
	private Integer ownScore;
	private Integer opponentScore;

	private List<SendouPlayer> ownTeamPlayers;
	private List<SendouPlayer> opponentTeamPlayers;
}

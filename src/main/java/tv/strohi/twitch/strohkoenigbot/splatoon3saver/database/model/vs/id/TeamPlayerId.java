package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id;

import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamPlayerId implements Serializable {
	private long teamId;
	private long playerId;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id;

import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultIdTeamOrder implements Serializable {
	private long resultId;
	private int teamOrder;
}

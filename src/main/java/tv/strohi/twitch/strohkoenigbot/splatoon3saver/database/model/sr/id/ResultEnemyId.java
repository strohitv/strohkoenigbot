package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id;

import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultEnemyId implements Serializable {
	private long resultId;
	private long enemyId;
}

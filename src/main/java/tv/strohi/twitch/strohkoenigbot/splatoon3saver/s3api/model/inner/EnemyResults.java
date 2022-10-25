package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class EnemyResults implements Serializable {
	private Integer defeatCount;
	private Integer teamDefeatCount;
	private Integer popCount;
	private IdAndNameAndImage enemy;
}

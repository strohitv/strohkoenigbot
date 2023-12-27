package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultEnemyId;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_enemy")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ResultEnemyId.class)
public class Splatoon3SrResultEnemy {
	@Id
	private long resultId;

	@Id
	private long enemyId;

	private Integer spawnCount;

	private Integer teamDestroyCount;

	private Integer ownDestroyCount;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "enemy_id", insertable = false, updatable = false)
	private Splatoon3SrEnemy enemy;
}

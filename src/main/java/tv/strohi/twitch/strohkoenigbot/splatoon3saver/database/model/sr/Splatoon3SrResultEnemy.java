package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultEnemyId;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_enemy")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(ResultEnemyId.class)
public class Splatoon3SrResultEnemy {
	@Id
	@Column(name = "result_id")
	private long resultId;

	@Id
	@Column(name = "enemy_id")
	private long enemyId;

	private Integer spawnCount;

	private Integer teamDestroyCount;

	private Integer ownDestroyCount;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "enemy_id", nullable = false, insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrEnemy enemy;
}

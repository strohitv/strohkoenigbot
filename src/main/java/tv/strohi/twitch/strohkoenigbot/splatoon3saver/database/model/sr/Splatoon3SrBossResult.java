package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_boss_result")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = {"result", "boss"})
public class Splatoon3SrBossResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private boolean defeated;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrResult result;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrBoss boss;
}

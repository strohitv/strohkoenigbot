package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_sr_boss")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = {"rotations", "bossResults"})
public class Splatoon3SrBoss {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private String name;

	private Integer enemyId;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id")
	@EqualsAndHashCode.Exclude
	private Image image;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "boss")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrRotation> rotations;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "boss")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrBossResult> bossResults;
}

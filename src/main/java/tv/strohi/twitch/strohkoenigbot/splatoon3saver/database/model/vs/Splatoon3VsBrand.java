package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_brand")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsBrand {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private String name;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id")
	@EqualsAndHashCode.Exclude
	private Image image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "favored_ability_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility favoredAbility;
}

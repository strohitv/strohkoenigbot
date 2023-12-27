package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_ability")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsAbility {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String name;

	private String description;

	private Long imageId;

	private Boolean isFillerAbility;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", insertable = false, updatable = false)
	private Image image;
}

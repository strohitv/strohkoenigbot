package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_gear")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsGear {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String type;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "original_image_id")
	@EqualsAndHashCode.Exclude
	private Image originalImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "thumbnail_image_id")
	@EqualsAndHashCode.Exclude
	private Image thumbnailImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brand_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsBrand brand;
}

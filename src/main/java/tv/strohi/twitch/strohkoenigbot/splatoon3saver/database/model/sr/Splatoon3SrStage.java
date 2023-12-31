package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_sr_stage")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "rotations")
public class Splatoon3SrStage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private String name;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", nullable = false)
	private Image image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "thumbnail_image_id", nullable = false)
	private Image shortenedThumbnailImage;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "stage")
	private List<Splatoon3SrRotation> rotations;
}

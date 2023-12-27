package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_sr_stage")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3SrStage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private String name;

	private Long imageId;

	private Long shortenedThumbnailImageId;

	// ---

	@ManyToOne
	@JoinColumn(name = "image_id", nullable = false, insertable = false, updatable = false)
	private Image image;

	@ManyToOne
	@JoinColumn(name = "thumbnail_image_id", nullable = false, insertable = false, updatable = false)
	private Image shortenedThumbnailImage;

	// ---

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", insertable = false, updatable = false)
	private List<Splatoon3SrRotation> rotations;
}

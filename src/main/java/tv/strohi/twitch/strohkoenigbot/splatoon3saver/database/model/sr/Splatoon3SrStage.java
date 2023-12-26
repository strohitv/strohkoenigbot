package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.ShortenedImage;

import javax.persistence.*;

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

	private Long shortenedImageId;

	private Long shortenedThumbnailImageId;

	// ---
	@ManyToOne
	@JoinColumn(name = "shortened_image_id")
	private ShortenedImage shortenedImage;

	@ManyToOne
	@JoinColumn(name = "shortened_thumbnail_image_id")
	private ShortenedImage shortenedThumbnailImage;
}

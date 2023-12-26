package tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.ShortenedImage;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_stage")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsStage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private Integer apiVsStageId;

	private String name;

	private Long shortenedImageId;

	private String shortenedJson;

	// ---
	@ManyToOne
	@JoinColumn(name = "shortened_image_id")
	private ShortenedImage shortenedImage;
}

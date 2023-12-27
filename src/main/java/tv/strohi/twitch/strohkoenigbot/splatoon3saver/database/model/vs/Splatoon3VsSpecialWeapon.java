package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_special_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsSpecialWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private String name;

	private Long imageId;

	private Integer maskingImageWidth;

	private Integer maskingImageHeight;

	private Long maskingImageId;

	private Long overlayImageId;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", insertable = false, updatable = false)
	private Image image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "masking_image_id", insertable = false, updatable = false)
	private Image maskingImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "overlay_image_id", insertable = false, updatable = false)
	private Image overlayImage;
}

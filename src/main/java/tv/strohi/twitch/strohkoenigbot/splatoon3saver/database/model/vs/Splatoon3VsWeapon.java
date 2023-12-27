package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private String name;

	private Long imageId;

	private Long subWeaponId;

	private Long specialWeaponId;

	private Long image2DId;

	private Long image2DThumbnailId;

	private Long image3DId;

	private Long image3DThumbnailId;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", insertable = false, updatable = false)
	private Image image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_2_d_id", insertable = false, updatable = false)
	private Image image2D;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_2_d_thumbnail_id", insertable = false, updatable = false)
	private Image image2DThumbnail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_3_d_id", insertable = false, updatable = false)
	private Image image3D;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_3_d_thumbnail_id", insertable = false, updatable = false)
	private Image image3DThumbnail;
}

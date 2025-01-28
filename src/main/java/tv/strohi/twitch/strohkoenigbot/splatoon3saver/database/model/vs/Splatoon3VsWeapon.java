package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsWeapon {
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
	@JoinColumn(name = "sub_weapon_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsSubWeapon subWeapon;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "special_weapon_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsSpecialWeapon specialWeapon;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_2_d_id")
	@EqualsAndHashCode.Exclude
	private Image image2D;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_2_d_thumbnail_id")
	@EqualsAndHashCode.Exclude
	private Image image2DThumbnail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_3_d_id")
	@EqualsAndHashCode.Exclude
	private Image image3D;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_3_d_thumbnail_id")
	@EqualsAndHashCode.Exclude
	private Image image3DThumbnail;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_vs_special_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsSpecialWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private String name;

	private Integer maskingImageWidth;

	private Integer maskingImageHeight;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "specialWeapon")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3VsSpecialBadgeWins> badgeWins;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id")
	@EqualsAndHashCode.Exclude
	private Image image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "masking_image_id")
	@EqualsAndHashCode.Exclude
	private Image maskingImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "overlay_image_id")
	@EqualsAndHashCode.Exclude
	private Image overlayImage;
}

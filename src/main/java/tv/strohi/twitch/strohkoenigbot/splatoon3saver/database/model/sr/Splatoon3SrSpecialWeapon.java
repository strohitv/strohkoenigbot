package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_special_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3SrSpecialWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private Long weaponId;

	private String name;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Image image;
}

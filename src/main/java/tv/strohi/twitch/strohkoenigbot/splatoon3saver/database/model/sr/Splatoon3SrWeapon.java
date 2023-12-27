package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity(name = "splatoon_3_sr_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3SrWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String name;

	private Long shortenedImageId;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", nullable = false, insertable = false, updatable = false)
	private Image image;

	// ---

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_1_id", insertable = false, updatable = false)
	private List<Splatoon3SrRotation> rotationsWeapon1;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_2_id", insertable = false, updatable = false)
	private List<Splatoon3SrRotation> rotationsWeapon2;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_3_id", insertable = false, updatable = false)
	private List<Splatoon3SrRotation> rotationsWeapon3;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_4_id", insertable = false, updatable = false)
	private List<Splatoon3SrRotation> rotationsWeapon4;

	public List<Splatoon3SrRotation> getAllRotations() {
		return Stream.of(rotationsWeapon1, rotationsWeapon2, rotationsWeapon3, rotationsWeapon4)
			.flatMap(List::stream)
			.sorted(Comparator.comparing(Splatoon3SrRotation::getStartTime))
			.collect(Collectors.toList());
	}
}

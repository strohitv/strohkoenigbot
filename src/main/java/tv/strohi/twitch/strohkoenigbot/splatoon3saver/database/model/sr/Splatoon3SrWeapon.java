package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
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
@Builder(toBuilder = true)
@ToString(exclude = {"rotationsWeapon1","rotationsWeapon2","rotationsWeapon3","rotationsWeapon4"})
public class Splatoon3SrWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Image image;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "weapon1")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrRotation> rotationsWeapon1;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "weapon2")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrRotation> rotationsWeapon2;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "weapon3")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrRotation> rotationsWeapon3;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "weapon4")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrRotation> rotationsWeapon4;

	public List<Splatoon3SrRotation> getAllRotations() {
		return Stream.of(rotationsWeapon1, rotationsWeapon2, rotationsWeapon3, rotationsWeapon4)
			.flatMap(List::stream)
			.sorted(Comparator.comparing(Splatoon3SrRotation::getStartTime))
			.collect(Collectors.toList());
	}
}

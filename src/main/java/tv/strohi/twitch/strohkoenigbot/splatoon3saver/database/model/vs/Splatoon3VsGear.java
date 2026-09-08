package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_gear")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsGear {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String type;

	private int gearLevel;

	private int currentExperience;

	private int previousExperience;

	private int goalExperience;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "original_image_id")
	@EqualsAndHashCode.Exclude
	private Image originalImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "thumbnail_image_id")
	@EqualsAndHashCode.Exclude
	private Image thumbnailImage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brand_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsBrand brand;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "main_ability")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility mainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sub_ability_1")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility subAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sub_ability_2")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility subAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sub_ability_3")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility subAbility3;
}

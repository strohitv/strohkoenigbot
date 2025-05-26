package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id.SpecialWeaponIdDay;

import javax.persistence.*;
import java.time.LocalDate;

@Entity(name = "splatoon_3_vs_special_badge_wins")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(SpecialWeaponIdDay.class)
public class Splatoon3VsSpecialBadgeWins {
	@Id
	@Column(name = "special_weapon_id")
	private Long specialWeaponId;

	@Id
	@Column(name = "stat_day", columnDefinition = "DATE")
	private LocalDate statDay;

	private Integer winCount;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "special_weapon_id", insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3VsSpecialWeapon specialWeapon;
}

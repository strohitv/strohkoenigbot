package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_wave_used_special_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3SrResultWaveUsedSpecialWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns({
		@JoinColumn(name = "result_id", nullable = false),
		@JoinColumn(name = "wave_number", nullable = false)
	})
	@EqualsAndHashCode.Exclude
	private Splatoon3SrResultWave resultWave;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "special_weapon_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrSpecialWeapon specialWeapon;
}

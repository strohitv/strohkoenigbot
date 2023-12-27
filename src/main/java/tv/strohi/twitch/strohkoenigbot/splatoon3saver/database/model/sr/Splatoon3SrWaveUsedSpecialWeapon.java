package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_wave_used_special_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3SrWaveUsedSpecialWeapon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long resultId;

	private Integer waveNumber;

	private Long specialWeaponId;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns({
		@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false),
		@JoinColumn(name = "wave_number", nullable = false, insertable = false, updatable = false)
	})
	private Splatoon3SrResultWave resultWave;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "special_weapon_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrSpecialWeapon specialWeapon;
}

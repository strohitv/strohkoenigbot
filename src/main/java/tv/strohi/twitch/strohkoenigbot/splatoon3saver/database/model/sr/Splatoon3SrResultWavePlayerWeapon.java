package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultWavePlayerWeaponId;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_wave_player_weapon")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ResultWavePlayerWeaponId.class)
public class Splatoon3SrResultWavePlayerWeapon {
	@Id
	private long resultId;

	@Id
	private int waveNumber;

	@Id
	private long playerId;

	@Id
	private long weaponId;

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
	@JoinColumn(name = "player_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3Player player;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_id", insertable = false, updatable = false)
	private Splatoon3SrWeapon weapon;
}

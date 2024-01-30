package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultIdWave;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_wave")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(ResultIdWave.class)
public class Splatoon3SrResultWave {
	@Id
	@Column(name = "result_id")
	private long resultId;

	@Id
	@Column(name = "wave_number")
	private int waveNumber;

	private Integer waterLevel;

	private Integer goldenEggsRequired;

	private Integer goldenEggsSpawned;

	private Integer goldenEggsDelivered;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_wave_id")
	private Splatoon3SrEventWave eventWave;
}

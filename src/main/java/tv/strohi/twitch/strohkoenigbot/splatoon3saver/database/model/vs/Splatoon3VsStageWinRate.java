package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_vs_stage_win_rate")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsStageWinRate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant time;

	private Double winRateZones;

	private Double winRateTower;

	private Double winRateRain;

	private Double winRateClams;

	private Double winRateTurf;

	// ---
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3VsStage stage;
}

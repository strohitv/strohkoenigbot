package tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_vs_stage_win_rate")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsStageWinRate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long stageId;

	private Instant time;

	private Double winRateZones;

	private Double winRateTower;

	private Double winRateRain;

	private Double winRateClams;

	private Double winRateTurf;

	// ---
	@ManyToOne
	@JoinColumn(name = "stage_id")
	private Splatoon3VsStage stage;
}

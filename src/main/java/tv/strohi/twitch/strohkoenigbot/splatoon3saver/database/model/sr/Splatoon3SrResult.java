package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_sr_result")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3SrResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private Instant playedTime;

	private Long rotationId;

	private Boolean successful;

	private Long bossId;

	private Long stageId;

	private Long afterGradeId;

	private Integer afterGradePoint;

	private Integer jobPoint;

	private Integer jobScore;

	private Double jobRate;

	private Integer jobBonus;

	private Integer earnedGoldScales;

	private Integer earnedSilverScales;

	private Integer earnedBronzeScales;

	private Integer smellMeter;

	private Double dangerRate;

	private String scenarioCode;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrRotation rotation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrBoss boss;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrStage stage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "after_grade_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrGrade afterGrade;
}

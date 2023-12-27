package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3Mode;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity(name = "splatoon_3_vs_result")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private Instant playedTime;

	private Integer duration;

	private String ownJudgement;

	private String knockOut;

	private Long modeId;

	private Long ruleId;

	private Long rotationId;

	private Long stageId;

	private String shortenedJson;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id", insertable = false, updatable = false)
	private Splatoon3Mode mode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rule_id", insertable = false, updatable = false)
	private Splatoon3VsRule rule;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id", insertable = false, updatable = false)
	private Splatoon3VsRotation rotation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", insertable = false, updatable = false)
	private Splatoon3VsStage stage;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
		name = "splatoon_3_vs_result_award",
		joinColumns = @JoinColumn(name = "result_id"),
		inverseJoinColumns = @JoinColumn(name = "award_id")
	)
	private List<Splatoon3VsAward> awards;
}

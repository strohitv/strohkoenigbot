package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3Mode;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity(name = "splatoon_3_vs_rotation")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsRotation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long stage1Id;

	private Long stage2Id;

	private Long modeId;

	private Long ruleId;

	private Instant startTime;

	private Instant endTime;

	private Long eventRegulationId;

	private String shortenedJson;

	// ---

	@ManyToOne
	@JoinColumn(name = "stage_1_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3VsStage stage1;

	@ManyToOne
	@JoinColumn(name = "stage_2_id", insertable = false, updatable = false)
	private Splatoon3VsStage stage2;

	@ManyToOne
	@JoinColumn(name = "mode_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3Mode mode;

	@ManyToOne
	@JoinColumn(name = "rule_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3VsRule rule;

	@ManyToOne
	@JoinColumn(name = "event_regulation_id", insertable = false, updatable = false)
	private Splatoon3VsEventRegulation eventRegulation;

	// ---

	@OneToMany
	@JoinColumn(name = "rotation_id", insertable = false, updatable = false)
	private List<Splatoon3VsRotationSlot> slots;
}

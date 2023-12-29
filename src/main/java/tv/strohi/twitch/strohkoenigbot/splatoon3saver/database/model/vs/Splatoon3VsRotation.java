package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity(name = "splatoon_3_vs_rotation")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsRotation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant startTime;

	private Instant endTime;

	@Lob
	private String shortenedJson;

	// ---

	@ManyToOne
	@JoinColumn(name = "stage_1_id", nullable = false)
	private Splatoon3VsStage stage1;

	@ManyToOne
	@JoinColumn(name = "stage_2_id")
	private Splatoon3VsStage stage2;

	@ManyToOne
	@JoinColumn(name = "mode_id", nullable = false)
	private Splatoon3VsMode mode;

	@ManyToOne
	@JoinColumn(name = "rule_id", nullable = false)
	private Splatoon3VsRule rule;

	@ManyToOne
	@JoinColumn(name = "event_regulation_id")
	private Splatoon3VsEventRegulation eventRegulation;

	// ---

	@OneToMany
	@JoinColumn(name = "rotation_id")
	private List<Splatoon3VsRotationSlot> slots;
}

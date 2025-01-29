package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity(name = "splatoon_3_vs_result")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "teams")
public class Splatoon3VsResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private Instant playedTime;

	private Integer duration;

	private String ownJudgement;

	private String knockout;

	@Lob
	private String shortenedJson;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "result")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3VsResultTeam> teams;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsMode mode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rule_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsRule rule;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsRotation rotation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsStage stage;

	@ManyToMany(fetch = FetchType.EAGER)
	@Fetch(value = FetchMode.SUBSELECT)
	@JoinTable(
		name = "splatoon_3_vs_result_award",
		joinColumns = @JoinColumn(name = "result_id"),
		inverseJoinColumns = @JoinColumn(name = "award_id")
	)
	@EqualsAndHashCode.Exclude
	private List<Splatoon3VsAward> awards;
}

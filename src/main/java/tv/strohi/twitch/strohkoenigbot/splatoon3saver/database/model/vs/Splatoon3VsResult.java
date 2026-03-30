package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.time.Instant;
import java.util.Base64;
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

	private boolean hasPower;

	private Double power;

	@Lob
	private String shortenedJson;

	private String replayCode;

	private boolean mmrLoadFailed;

	private Double mmr;

	@Lob
	private String replayJson;

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

	public boolean apiIdEquals(String otherApiId) {
		if (apiId == null && otherApiId == null) {
			return true;
		}

		if (apiId == null) {
			// otherApiId not null
			return false;
		}

		if (otherApiId == null) {
			// apiId not null
			return false;
		}

		var decodedApiIdParts = new String(Base64.getDecoder().decode(apiId)).split(":");
		var decodedOtherApiIdParts = new String(Base64.getDecoder().decode(otherApiId)).split(":");

		var realApiId = decodedApiIdParts[decodedApiIdParts.length - 1];
		var realOtherApiId = decodedOtherApiIdParts[decodedOtherApiIdParts.length - 1];

		return realApiId.equals(realOtherApiId);
	}
}

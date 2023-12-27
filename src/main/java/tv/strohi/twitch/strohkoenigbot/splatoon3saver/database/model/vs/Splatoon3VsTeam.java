package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_team")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsTeam {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long resultId;

	private Integer order;

	private Double inkColorR;

	private Double inkColorG;

	private Double inkColorB;

	private Double inkColorA;

	private String judgement;

	private Double paintRatio;

	private Integer score;

	private Integer tricolorGainedUltraSignals;

	private String tricolorRole;

	private String splatfestTeamName;

	private Integer splatfestStreakWinCount;

	private String splatfestUniformName;

	private Double splatfestUniformBonusRate;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", insertable = false, updatable = false)
	private Splatoon3VsResult result;
}

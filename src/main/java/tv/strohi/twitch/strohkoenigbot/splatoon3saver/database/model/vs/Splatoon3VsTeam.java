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
@Builder(toBuilder = true)
public class Splatoon3VsTeam {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Integer order;

	@Column(name = "ink_color_r")
	private Double inkColorR;

	@Column(name = "ink_color_g")
	private Double inkColorG;

	@Column(name = "ink_color_b")
	private Double inkColorB;

	@Column(name = "ink_color_a")
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
	@JoinColumn(name = "result_id")
	private Splatoon3VsResult result;
}

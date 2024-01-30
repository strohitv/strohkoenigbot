package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id.ResultIdTeamOrder;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_vs_result_team")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(ResultIdTeamOrder.class)
@ToString(exclude = "teamPlayers")
public class Splatoon3VsResultTeam {
	@Id
	@Column(name = "result_id")
	private Long resultId;

	@Id
	@Column(name = "team_order")
	private Integer teamOrder;

	private Boolean isMyTeam;

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
	@JoinColumn(name = "result_id", insertable = false, updatable = false)
	private Splatoon3VsResult result;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "team")
	private List<Splatoon3VsResultTeamPlayer> teamPlayers;
}

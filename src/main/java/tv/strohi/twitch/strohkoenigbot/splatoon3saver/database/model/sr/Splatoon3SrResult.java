package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.interfaces.SplatoonGame;

import javax.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Entity(name = "splatoon_3_sr_result")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = {"shortenedJson", "mode", "rotation", "boss", "stage", "afterGrade", "players", "waves", "enemyStats", "bossResults"})
public class Splatoon3SrResult implements SplatoonGame {
	private static int GAME_DURATION_W1_START = Long.valueOf(Duration.ofMinutes(0).plusSeconds(30).toSeconds()).intValue();
	private static int GAME_DURATION_W2_START = Long.valueOf(Duration.ofMinutes(2).plusSeconds(30).toSeconds()).intValue();
	private static int GAME_DURATION_W3_START = Long.valueOf(Duration.ofMinutes(4).plusSeconds(30).toSeconds()).intValue();
	private static int GAME_DURATION_KING_START = Long.valueOf(Duration.ofMinutes(6).plusSeconds(30).toSeconds()).intValue();
	private static int GAME_DURATION_REGULAR_END = Long.valueOf(Duration.ofMinutes(6).plusSeconds(20).toSeconds()).intValue();
	private static int GAME_DURATION_KING_END = Long.valueOf(Duration.ofMinutes(8).plusSeconds(17).toSeconds()).intValue();
	private static List<Integer> GAME_DURATION_STARTS = List.of(GAME_DURATION_W1_START, GAME_DURATION_W2_START, GAME_DURATION_W3_START, GAME_DURATION_KING_START, GAME_DURATION_KING_END);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiId;

	private Instant playedTime;

	private Boolean successful;

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

	@Lob
	private String shortenedJson;

	@Override
	public Integer getDuration() {
		if (successful) {
			return waves.size() == 4 ? GAME_DURATION_KING_END : GAME_DURATION_REGULAR_END;
		} else {
			return GAME_DURATION_STARTS.get(waves.size()) + (GAME_DURATION_STARTS.get(waves.size() + 1) - GAME_DURATION_STARTS.get(waves.size()));
		}
	}

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrMode mode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrRotation rotation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrBoss boss;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrStage stage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "after_grade_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrGrade afterGrade;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "result")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrResultPlayer> players;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "result")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrResultWave> waves;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "result")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrResultEnemy> enemyStats;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "result")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3SrBossResult> bossResults;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class CoopHistoryDetail implements Serializable {
	private String __typename;

	private String id;
	private Id nextHistoryDetail;
	private Id previousHistoryDetail;

	private String playedTime;
	private Double dangerRate;
	private IdAndName afterGrade;
	private Integer afterGradePoint;
	private BossResult bossResult;
	private ScaleCount scale;
	private Integer jobBonus;
	private Integer resultWave;
	private Integer jobScore;
	private Integer jobPoint;
	private Double jobRate;

	private CoopResult myResult;
	private List<CoopResult> memberResults;
	private List<EnemyResults> enemyResults;
	private List<WaveResults> waveResults;
	private Integer smellMeter;

	private String rule;
	private CoopStage coopStage;
	private List<NameAndImage> weapons;

	private CoopBoss boss;

	private String scenarioCode;

	public Instant getPlayedTimeAsInstant() {
		try {
			return Instant.parse(playedTime);
		} catch (Exception ignored) {
			return null;
		}
	}

	public CoopBoss tryGetBoss() {
		if (boss != null) {
			return boss;
		}

		return bossResult != null
			? bossResult.getBoss()
			: null;
	}
}

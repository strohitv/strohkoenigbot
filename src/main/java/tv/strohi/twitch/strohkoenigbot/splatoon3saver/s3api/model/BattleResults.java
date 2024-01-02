package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//@JsonIgnoreProperties(ignoreUnknown = true)
public class BattleResults {
	private BattleResultsData data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleResultsData {
		private BattleHistories latestBattleHistories;
		private BattleHistories regularBattleHistories;
		private BattleHistories bankaraBattleHistories;
		@JsonProperty("xBattleHistories")
		private BattleHistories xBattleHistories;
		private BattleHistories eventBattleHistories;
		private BattleHistories privateBattleHistories;
		private BattleHistories coopResult;
		private Nothing currentFest;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleHistories {
		private Summary summary;
		private HistoryGroups historyGroupsOnlyFirst;
		private HistoryGroups historyGroups;

		// Salmon Run
		private Double regularAverageClearWave;
		private IdAndName regularGrade;
		private Integer regularGradePoint;
		private Gear monthlyGear;
		private ScaleCount scale;
		private SalmonRunPointCard pointCard;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroups {
		private HistoryGroupsNodes[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodes {
		private HistoryGroupsNodesDetails historyDetails;

		private AnarchyMatchChallenge bankaraMatchChallenge;
		@JsonProperty("xMatchMeasurement")
		private XMatchMeasurement xMatchMeasurement;

		// challenge
		private LeagueMatchHistoryGroup leagueMatchHistoryGroup;

		// Salmon run
		private String startTime;
		private String endTime;

		private String mode;
		private String rule;

		private SalmonRunHighestResult highestResult;

		private Integer playCount;

		@JsonIgnore
		public Instant getStartTimeAsInstant() {
			return Instant.parse(startTime);
		}

		@JsonIgnore
		public Instant getEndTimeAsInstant() {
			return Instant.parse(endTime);
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class LeagueMatchHistoryGroup {
		private IdAndName leagueMatchEvent;
		private VsRule vsRule;
		private String teamComposition;
		private Double myLeaguePower;
		private String measurementState;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodesDetails {
		private HistoryGroupMatch[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResult {
		private SingleMatchResultData data;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResultData {
		private HistoryGroupMatch vsHistoryDetail;
		private HistoryGroupMatch coopHistoryDetail;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SalmonRunPointCard {
		private Integer defeatBossCount;
		private Long deliverCount;
		private Integer goldenDeliverCount;
		private Integer playCount;
		private Integer rescueCount;
		private Integer regularPoint;
		private Integer totalPoint;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Summary {
		private Double assistAverage;
		private Double deathAverage;
		private Double killAverage;
		private Integer perUnitTimeMinute;
		private Double specialAverage;

		@JsonProperty("xPowerAr")
		private XPower xPowerAr;
		@JsonProperty("xPowerCl")
		private XPower xPowerCl;
		@JsonProperty("xPowerGl")
		private XPower xPowerGl;
		@JsonProperty("xPowerLf")
		private XPower xPowerLf;

		private Integer win;
		private Integer lose;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class XPower {
		@JsonProperty("lastXPower")
		private Double lastXPower;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SalmonRunHighestResult {
		private IdAndName grade;
		private Integer gradePoint;
		private Integer jobScore;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AnarchyMatchChallenge {
		private Integer winCount;
		private Integer loseCount;
		private Integer maxWinCount;
		private Integer maxLoseCount;
		private String state;
		private Boolean isPromo;
		private Boolean isUdemaeUp;
		private String udemaeAfter;
		private Integer earnedUdemaePoint;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class XMatchMeasurement {
		private String state;
		@JsonProperty("xPowerAfter")
		private Double xPowerAfter;
		private Boolean isInitial;
		private Integer winCount;
		private Integer loseCount;
		private Integer maxInitialBattleCount;
		private Integer maxWinCount;
		private Integer maxLoseCount;
		private VsRule vsRule;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupMatch {
		private String __typename;

		private String id;
		private String playedTime;
		private Integer duration;
		private VsMode vsMode;
		private VsRule vsRule;
		private VsStage vsStage;
		private String Judgement;
		private Player player;
		private String knockout;
		private Team myTeam;
		private Team[] otherTeams;
		private Id nextHistoryDetail;
		private Id previousHistoryDetail;

		private Award[] awards;

		private String udemae;
		private AnarchyMatch bankaraMatch;

		@JsonProperty("xMatch")
		private XMatch xMatch;

		private FestMatch festMatch;

		private LeagueMatchDetails leagueMatch;

		// Salmon Run
		private Weapon[] weapons;
		private Integer resultWave;
		private CoopStage coopStage;
		private IdAndName afterGrade;
		private Integer afterGradePoint;
		private String gradePointDiff;
		private SalmonRunBossResult bossResult;
		private Result myResult;
		private Result[] memberResults;

		// TODO salmon run fields have to be added!!
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SalmonRunBossResult {
		private Boolean hasDefeatBoss;
		private IdAndName boss;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AnarchyMatch {
		private Integer earnedUdemaePoint;
		private String mode;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class XMatch {
		private Double lastXPower;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class FestMatch {
		private String dragonMatchType;
		private Integer contribution;
		private Integer jewel;
		private double myFestPower;
	}
}

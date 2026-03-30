package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Nothing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InksightReplay {
	private int version;
	private String overallVerdict;
	private Boolean noIntegrityData;
	@JsonProperty("has_flag")
	private Boolean hasFlag;

	private MatchData match;
	private List<TeamData> teams;
	private Map<String, Map<String, Integer>> killMatrix;
	private List<KillCounts> killCountsIndexed;
	private Map<String, List<Integer>> teamColors;


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class MatchData {
		private String id;
		private String date;
		private String mode;
		private Long modeId;
		private String rule;
		private Long ruleId;
		private String stage;
		private String stageKey;
		private String duration;
		private String gameVersion;
		private Long expectedIntegrityCycles;
		private Long winTeam;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class TeamData {
		private String name;
		private Long teamIndex;
		@JsonProperty("is_winner")
		private Boolean isWinner;
		private Integer score;
		private Integer paintPermille;
		private Integer paintPoints;
		private List<PlayerData> players;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class PlayerData {
		private String playerIndex;
		private String name;
		private String discriminator;
		@JsonProperty("is_recorder")
		private Boolean isRecorder;
		private String hardware;
		private String weapon;
		private Long weaponId;
		private Long model;
		private Nameplate namePlate;
		private PlayerStats stats;
		private Gear gear;
		private Anticheat anticheat;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class Nameplate {
		private Long adjective;
		private Long subject;
		private Long gender;
		private Long background;
		private Long badge1;
		private Long badge2;
		private Long badge3;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class PlayerStats {
		private Long paint;
		private Long kills;
		private Long assists;
		private Long deaths;
		private Long specials;
		private Long wastedSpecials;
		private Long boils;
		private Long medals;
		private List<Long> medalIds;
		private List<String> medalNames;
		@JsonProperty("x_power")
		private Double xPower;
		private Double mmr;

		@NonNull
		public Optional<Double> getXPower() {
			if (xPower == null || xPower < 500.0 || xPower > 6000.0) {
				return Optional.empty();
			} else {
				return Optional.of(xPower);
			}
		}

		@NonNull
		public Optional<Double> getMmr() {
			if (mmr == null || mmr < 500.0 || mmr > 6000.0) {
				return Optional.empty();
			} else {
				return Optional.of(mmr);
			}
		}
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class Gear {
		private GearPiece head;
		private GearPiece clothes;
		private GearPiece shoes;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class GearPiece {
		private String name;
		private Long id;
		private String mainSkill;
		private Long mainSkillId;
		private List<String> subSkills;
		private List<Long> subSkillIds;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class Anticheat {
		private String status;
		private List<Nothing> deviations;
		private List<Nothing> deviationLabels;
		private List<String> internalReports;
		private Nothing integritySummary;
		private Boolean skipDetected;
		private Boolean disconnected;
		private Long usableCycles;
	}


	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class KillCounts {
		private Integer attacker;
		private Integer victim;
		private Integer count;
	}
}




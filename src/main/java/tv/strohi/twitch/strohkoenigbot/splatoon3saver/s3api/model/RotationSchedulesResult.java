package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotationSchedulesResult implements Serializable {
	private RotationSchedulesResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private Node regularSchedules;
		private Node bankaraSchedules;
		@JsonProperty("xSchedules")
		private Node xSchedules;
		private EventNodes eventSchedules;
		private CoopGroupingSchedule coopGroupingSchedule;
		private Node festSchedules;

		private Fest currentFest;

		private Player currentPlayer;
		private StageData vsStages;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Node implements Serializable {
		private Rotation[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class EventNodes implements Serializable {
		private EventNode[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class EventNode implements Serializable {
		private LeagueMatchSetting leagueMatchSetting;
		private TimePeriod[] timePeriods;

		@JsonIgnore
		public Instant getEarliestOccurrence() {
			return Arrays.stream(timePeriods)
				.sorted(Comparator.comparing(TimePeriod::getStartTimeAsInstant))
				.map(TimePeriod::getStartTimeAsInstant)
				.findFirst()
				.orElse(Instant.now());
		}

		@JsonIgnore
		public Instant getLatestEnd() {
			return Arrays.stream(timePeriods)
				.sorted((a, b) -> b.getEndTimeAsInstant().compareTo(a.getEndTimeAsInstant()))
				.map(TimePeriod::getEndTimeAsInstant)
				.findFirst()
				.orElse(Instant.now());
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class LeagueMatchSetting implements Serializable {
		private String __isVsSetting;
		private String __typename;

		private LeagueMatchEvent leagueMatchEvent;

		private VsStage[] vsStages;
		private VsRule vsRule;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class LeagueMatchEvent implements Serializable {
		private String leagueMatchEventId;
		private String name;
		private String desc;
		private String regulationUrl;
		private String regulation;
		private String id;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class TimePeriod implements Serializable {
		private String startTime;
		private String endTime;

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
	// @Accessors(fluent = true)
	public static class Player implements Serializable {
		private Image userIcon;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class StageData implements Serializable {
		private VsStage[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Fest implements Serializable {
		private String id;
		private String title;
		private String startTime;
		private String endTime;
		private String midtermTime;
		private String state;
		private Team[] teams;
		private VsStage tricolorStage;

		public Instant getStartTimeAsInstant() {
			return Instant.parse(startTime);
		}

		public Instant getEndTimeAsInstant() {
			return Instant.parse(endTime);
		}

		public Instant getMidTermTimeAsInstant() {
			return Instant.parse(midtermTime);
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Team implements Serializable {
		private String id;
		private Color color;
		private String myVoteState;
	}
}

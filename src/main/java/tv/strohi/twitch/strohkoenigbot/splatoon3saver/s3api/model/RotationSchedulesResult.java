package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.io.Serializable;

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
		private Node leagueSchedules;
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

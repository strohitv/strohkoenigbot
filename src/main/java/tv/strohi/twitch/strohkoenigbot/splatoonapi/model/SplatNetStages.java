package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetStages {
	private SplatNetRotation[] league;
	private SplatNetRotation[] gachi;
	private SplatNetRotation[] regular;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetRotation {
		private long id;
		private long start_time;
		private long end_time;

		private SplatNetMatchRule rule;
		private SplatNetStage stage_a;
		private SplatNetStage stage_b;
		private KeyNameTuple game_mode;

		@JsonIgnore
		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		@JsonIgnore
		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonStages {
	private SplatoonRotation[] league;
	private SplatoonRotation[] gachi;
	private SplatoonRotation[] regular;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonRotation {
		private long id;
		private int start_time;
		private int end_time;

		private SplatoonMatchRule rule;
		private SplatoonStage stage_a;
		private SplatoonStage stage_b;
		private KeyNameTuple game_mode;

		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}
		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}
	}
}

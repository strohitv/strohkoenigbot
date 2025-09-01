package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PastSeasonEdge {
	public PastSeasonNode node;
	public String cursor;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PastSeasonNode {
		public String id;
		public String name;
		public String __typename;
		public String startTime;
		public String endTime;

		public Instant getStartTimeAsInstant() {
			return Instant.parse(startTime);
		}

		public Instant getEndTimeAsInstant() {
			return Instant.parse(endTime);
		}
	}
}

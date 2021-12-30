package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetMatchResultsCollection {
	private String unique_id;
	private SplatNetMatchResult[] results;
	private SplatNetMatchResultsSummary summary;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetMatchResultsSummary {
		private int count;
		private int victory_count;
		private double special_count_average;
		private double victory_rate;
		private double kill_count_average;
		private int defeat_count;
		private double assist_count_average;
		private double death_count_average;
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonMerchandises {
	SplatoonMerchandise ordered_info;
	SplatoonMerchandise[] merchandises;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonMerchandise {
		String id;
		String kind;
		Integer price;
		Integer end_time;

		SplatoonGear gear;
		SplatoonGearSkill skill;

		public Instant getEndTime() {
			return end_time != null ? Instant.ofEpochSecond(end_time) : null;
		}
	}
}

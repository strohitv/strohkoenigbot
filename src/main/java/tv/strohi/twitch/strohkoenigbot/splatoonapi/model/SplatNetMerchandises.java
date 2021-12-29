package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetMerchandises {
	SplatNetMerchandise ordered_info;
	SplatNetMerchandise[] merchandises;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetMerchandise {
		String id;
		String kind;
		Integer price;
		Integer end_time;

		SplatNetGear gear;
		SplatNetGearSkill skill;

		public Instant getEndTime() {
			return end_time != null ? Instant.ofEpochSecond(end_time) : null;
		}
	}
}

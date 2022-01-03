package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetSalmonRunSchedules {
	private SplatNetSchedule[] schedules;
	private SplatNetScheduleDetail[] details;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetSchedule {
		private Long start_time;
		private Long end_time;

		@JsonIgnore
		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		@JsonIgnore
		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetScheduleDetail {
		private Long start_time;
		private Long end_time;

		private SplatNetStage stage;

		private WeaponDetail[] weapons;

		@JsonIgnore
		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		@JsonIgnore
		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}


		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class WeaponDetail {
			private String id;
			private SplatNetWeapon weapon;
		}
	}
}

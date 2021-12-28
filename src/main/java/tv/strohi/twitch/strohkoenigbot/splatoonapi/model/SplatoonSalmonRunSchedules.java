package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonSalmonRunSchedules {
	private SplatoonSchedule[] schedules;
	private SplatoonScheduleDetail[] details;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonSchedule {
		private Long start_time;
		private Long end_time;

		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonScheduleDetail {
		private Long start_time;
		private Long end_time;

		private SplatoonStage stage;

		private WeaponDetail[] weapons;

		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		public Instant getEndTimeAsInstant() {
			return Instant.ofEpochSecond(end_time);
		}


		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class WeaponDetail {
			private String id;
			private SplatoonWeapon weapon;
		}
	}
}

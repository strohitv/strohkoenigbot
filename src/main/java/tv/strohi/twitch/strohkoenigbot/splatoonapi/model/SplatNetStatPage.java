package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetStatPage {
	private Object challenges;
	private Object festivals;
	private SplatNetRecords records;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetRecords {
		private int recent_win_count;
		private int lose_count;
		private int recent_lose_count;
		private int win_count;
		private int recent_disconnect_count;

		private long total_paint_point_octa;
		private long start_time;
		private long update_time;

		private String unique_id;

		private Map<String, SplatNetWeaponStats> weapon_stats;
		private Map<String, SplatNetStageStats> stage_stats;

		private Object fes_results;
		private Object league_stats;
		private SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer player;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatNetWeaponStats {
			private int lose_count;
			private int win_count;

			private long last_use_time;
			private long total_paint_point;

			private double win_meter;
			private double max_win_meter;

			private SplatNetWeapon weapon;
		}


		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatNetStageStats {
			private SplatNetStage stage;
			private long last_play_time;

			// zones
			private int area_win;
			private int area_lose;

			// rainmaker
			private int hoko_win;
			private int hoko_lose;

			// tower control
			private int yagura_win;
			private int yagura_lose;

			// clam blitz
			private int asari_win;
			private int asari_lose;

			public int getZonesWinCount() {
				return area_win;
			}

			public int getZonesLoseCount() {
				return area_lose;
			}

			public int getRainmakerWinCount() {
				return hoko_win;
			}

			public int getRainmakerLoseCount() {
				return hoko_lose;
			}

			public int getTowerWinCount() {
				return yagura_win;
			}

			public int getTowerLoseCount() {
				return yagura_lose;
			}

			public int getClamsWinCount() {
				return asari_win;
			}

			public int getClamsLoseCount() {
				return asari_lose;
			}
		}
	}
}

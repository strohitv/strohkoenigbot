package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetXRankLeaderBoard {
	private SplatNetXRankModeLeaderBoard rainmaker;
	private SplatNetXRankModeLeaderBoard splat_zones;
	private SplatNetXRankModeLeaderBoard tower_control;
	private SplatNetXRankModeLeaderBoard clam_blitz;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetXRankModeLeaderBoard {
		private String season_id;
		private int top_rankings_count;
		private long start_time;
		private long end_time;
		private String status;

		private SplatNetMatchRule rule;

		private SplatNetXRankWeaponRanking weapon_ranking;
		private SplatNetXRankWeaponRanking[] top_rankings;
		private SplatNetXRankWeaponRanking my_ranking;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatNetXRankWeaponRanking {
			private boolean cheater;
			private String unique_id;
			private String principal_id;
			private double x_power;
			private String name;
			private String rank_change;
			private Integer rank;

			private SplatNetMatchResultsCollection.SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer.SplatNetWeapon weapon;
		}
	}
}

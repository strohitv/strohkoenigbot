package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonXRankLeaderBoard {
	private SplatoonXRankModeLeaderBoard rainmaker;
	private SplatoonXRankModeLeaderBoard splat_zones;
	private SplatoonXRankModeLeaderBoard tower_control;
	private SplatoonXRankModeLeaderBoard clam_blitz;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonXRankModeLeaderBoard {
		private String season_id;
		private int top_rankings_count;
		private long start_time;
		private long end_time;
		private String status;

		private SplatoonMatchResultsCollection.SplatoonMatchResult.SplatoonMatchRule rule;

		private SplatoonXRankWeaponRanking weapon_ranking;
		private SplatoonXRankWeaponRanking[] top_rankings;
		private SplatoonXRankWeaponRanking my_ranking;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonXRankWeaponRanking {
			private boolean cheater;
			private String unique_id;
			private String principal_id;
			private double x_power;
			private String name;
			private String rank_change;
			private Integer rank;

			private SplatoonMatchResultsCollection.SplatoonMatchResult.SplatoonPlayerResult.SplatoonPlayer.SplatoonWeapon weapon;
		}
	}
}

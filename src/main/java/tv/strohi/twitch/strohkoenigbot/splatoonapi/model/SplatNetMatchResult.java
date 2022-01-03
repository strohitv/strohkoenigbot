package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetMatchResult {
	private String battle_number;
	private long start_time;
	private int elapsed_time;

	private SplatNetStage stage;

	private String type;
	private KeyNameTuple game_mode;
	private SplatNetMatchRule rule;

	private SplatNetPlayerResult player_result;

	// how often did the player win in a row
	private int win_meter;
	private long weapon_paint_point;
	private int player_rank;
	private int star_rank;

	private KeyNameTuple my_team_result;
	private Integer my_team_count;
	private Double my_team_percentage;

	private KeyNameTuple other_team_result;
	private Integer other_team_count;
	private Double other_team_percentage;

	private Double x_power;
	private Double estimate_gachi_power;
	private Double estimate_x_power;

	private SplatNetUdemae udemae;
	private String rank;
	private String[] crown_players;

	// league
	private Double my_estimate_league_point;
	private Double other_estimate_league_point;
	private Double max_league_point;
	private Double league_point;
	private String tag_id;

	private SplatNetPlayerResult[] my_team_members;
	private SplatNetPlayerResult[] other_team_members;

	@JsonIgnore
	public Instant getStartTimeAsInstant() {
		return Instant.ofEpochSecond(start_time);
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetPlayerResult {
		private int special_count;
		private int game_paint_point;
		private int kill_count;
		private int death_count;
		private int sort_score;
		private int assist_count;

		private SplatNetPlayer player;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatNetPlayer {
			private String nickname;
			private String principal_id;
			private int star_rank;
			private int player_rank;

			private SplatNetPlayerType player_type;

			private SplatNetWeapon weapon;

			private SplatNetGear head;
			private SplatNetGear clothes;
			private SplatNetGear shoes;

			private SplatNetGearSkills head_skills;
			private SplatNetGearSkills clothes_skills;
			private SplatNetGearSkills shoes_skills;

			private SplatNetUdemae udemae;

			// only when loaded from stats tab api request
			private SplatNetUdemae udemae_zones;
			private SplatNetUdemae udemae_rainmaker;
			private SplatNetUdemae udemae_tower;
			private SplatNetUdemae udemae_clam;

			private Double max_league_point_pair;
			private Double max_league_point_team;

			@Data
			@NoArgsConstructor
			@AllArgsConstructor
			public static class SplatNetPlayerType {
				private String style;
				private String species;
			}

			@Data
			@NoArgsConstructor
			@AllArgsConstructor
			public static class SplatNetGearSkills {
				private SplatNetGearSkill main;
				private SplatNetGearSkill[] subs;
			}
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetUdemae {
		private boolean is_number_reached;
		private String name;
		private String s_plus_number;
		private boolean is_x;
		private int number;

		public boolean isIs_number_reached() {
			return is_number_reached;
		}

		public void setIs_number_reached(boolean is_number_reached) {
			this.is_number_reached = is_number_reached;
		}

		public boolean isIs_x() {
			return is_x;
		}

		public void setIs_x(boolean is_x) {
			this.is_x = is_x;
		}
	}
}

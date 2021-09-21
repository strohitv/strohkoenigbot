package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonMatchResultsCollection {
	private String unique_id;
	private SplatoonMatchResult[] results;
	private SplatoonMatchResultsSummary summary;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonMatchResult {
		private String battle_number;
		private long start_time;
		private int elapsed_time;

		private SplatoonStage stage;

		private String type;
		private KeyNameTuple game_mode;
		private SplatoonMatchRule rule;

		private SplatoonPlayerResult player_result;

		// how often did the player win in a row
		private int win_meter;
		private int weapon_paint_point;
		private int player_rank;
		private int star_rank;

		private KeyNameTuple my_team_result;
		private int my_team_count;
		private double my_team_percentage;

		private KeyNameTuple other_team_result;
		private int other_team_count;
		private double other_team_percentage;

		private Double x_power;
		private Double estimate_gachi_power;
		private Double estimate_x_power;

		private SplatoonUdemae udemae;
		private String rank;
		private String crown_players;

		public Instant getStartTimeAsInstant() {
			return Instant.ofEpochSecond(start_time);
		}

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonPlayerResult {
			private int special_count;
			private int game_paint_point;
			private int kill_count;
			private int death_count;
			private int sort_score;
			private int assist_count;

			private SplatoonPlayer player;

			@Data
			@NoArgsConstructor
			@AllArgsConstructor
			public static class SplatoonPlayer {
				private String nickname;
				private String principal_id;
				private int star_rank;
				private int player_rank;

				private SplatoonPlayerType player_type;

				private SplatoonWeapon weapon;

				private SplatoonGear head;
				private SplatoonGear clothes;
				private SplatoonGear shoes;

				private SplatoonGearSkills head_skills;
				private SplatoonGearSkills clothes_skills;
				private SplatoonGearSkills shoes_skills;

				private SplatoonUdemae udemae;

				@Data
				@NoArgsConstructor
				@AllArgsConstructor
				public static class SplatoonPlayerType {
					private String style;
					private String species;
				}

				@Data
				@NoArgsConstructor
				@AllArgsConstructor
				public static class SplatoonWeapon {
					private String id;
					private String name;
					private String image;
					private String thumbnail;

					private SplatoonWeaponDetail sub;
					private SplatoonWeaponDetail special;

					@Data
					@NoArgsConstructor
					@AllArgsConstructor
					public static class SplatoonWeaponDetail {
						private String id;
						private String name;
						private String image_a;
						private String image_b;
					}
				}

				@Data
				@NoArgsConstructor
				@AllArgsConstructor
				public static class SplatoonGear {
					private int rarity;
					private String id;
					private String kind;
					private String image;
					private String name;
					private String thumbnail;

					private SplatoonGearBrand brand;

					@Data
					@NoArgsConstructor
					@AllArgsConstructor
					public static class SplatoonGearBrand {
						private String id;
						private String name;
						private String image;

						private SplatoonFrequentSkill frequent_skill;

						@Data
						@NoArgsConstructor
						@AllArgsConstructor
						public static class SplatoonFrequentSkill {
							private String id;
							private String name;
							private String image;
						}
					}
				}

				@Data
				@NoArgsConstructor
				@AllArgsConstructor
				public static class SplatoonGearSkills {
					private SplatoonGearSkill main;
					private SplatoonGearSkill[] subs;

					@Data
					@NoArgsConstructor
					@AllArgsConstructor
					public static class SplatoonGearSkill {
						private String name;
						private String image;
						private String id;
					}
				}
			}
		}

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class KeyNameTuple {
			private String name;
			private String key;
		}

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonMatchRule {
			private String name;
			private String key;
			private String multiline_name;
		}

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonStage {
			private String id;
			private String name;
			private String image;
		}

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonUdemae {
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

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonMatchResultsSummary {
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

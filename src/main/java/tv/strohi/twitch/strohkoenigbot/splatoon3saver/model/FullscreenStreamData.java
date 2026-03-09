package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
public class FullscreenStreamData {
	public static FullscreenStreamData empty() {
		return FullscreenStreamData.builder().type(Type.NONE).build();
	}

	public enum Type {
		NONE, VS, SR
	}

	private Type type;

	private Long last_game_end_time;

	// todo wenn vs
	private GeneralStats general;
	private WeaponInfo weapon;
	private ClothingData clothing;
	private GameData game;
	private List<MapData> map_stats;

	// todo wenn sr


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class GeneralStats {
		private long wins;
		private long defeats;

		private String sub_weapon_image;
		private long sub_weapon_wins;
		private long sub_weapon_games;

		private String special_weapon_image;
		private int special_wins;
		private Integer special_wins_gained;

		private String anarchy_rank;
		private Double weapon_power; // Type not specified in TS code, kept as Object

		private Double x_zones;
		private Double x_tower;
		private Double x_rain;
		private Double x_clams;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class WeaponInfo {
		private String name;

		private String image;

		private List<KeyWinDefeatRate> stats;

		private long game_count;

		private int stars;

		private Integer exp_change;
		private Integer exp_now;

		private Double exp_start_ratio;
		private Double exp_change_ratio;
		private Double exp_left_ratio;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class WinDefeatRate {
		private long wins;
		private long wins_gained;

		private long defeats;
		private long defeats_gained;

		private double winrate;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class KeyWinDefeatRate {
		private String key;
		private WinDefeatRate win_defeat_rate;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class ClothingData {
		private ClothingInfo head;
		private ClothingInfo shirt;
		private ClothingInfo shoes;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class ClothingInfo {
		private String name;
		private String image;
		private int stars;
		private long game_count;

		private String main_image;
		private String sub_1_image;
		private String sub_2_image;
		private String sub_3_image;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class GameData {
		private String mode;
		private String modeIcon;
		private String rule;
		private String ruleIcon;
		private String stage;

		private List<TeamData> teams;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class TeamData {
		public enum Result {
			WIN(2), LOSE(0), SUPPORT(1);

			private final int value;

			Result(int value) {
				this.value = value;
			}

			public static int compare(Result a, Result b) {
				return Integer.compare(b.value, a.value);
			}
		}

		private Result result;
		private String result_str;
		private String color;

		private List<PlayerData> players;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class PlayerData {
		private String name;

		@JsonProperty("is_myself")
		private boolean is_myself;

		private String weapon_image;
		private String special_weapon_image;
		private String sub_weapon_image;

		private String head_main_image;
		private String shirt_main_image;
		private String shoes_main_image;

		private int kills;
		private int assists;
		private int deaths;
		private int specials;
		private int paint;

		private Long number_of_games;
	}


	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	@Builder(toBuilder = true)
	public static class MapData {
		private String name;
		private String image;

		private List<KeyWinDefeatRate> stats;
	}
}

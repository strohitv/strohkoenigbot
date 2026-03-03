package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

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

	public Type type;

	public Long last_game_end_time;

	// todo wenn vs
	public GeneralStats general;
	public WeaponInfo weapon;
	public ClothingData clothing;
	public GameData game;
	public List<MapData> map_stats;

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
		public long wins;
		public long defeats;

		public String special_weapon_image;
		public int special_wins;
		public Integer special_wins_gained;

		public String anarchy_rank;
		public Double weapon_power; // Type not specified in TS code, kept as Object

		public Double x_zones;
		public Double x_tower;
		public Double x_rain;
		public Double x_clams;
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
		public String name;

		public String image;

		public List<KeyWinDefeatRate> stats;

		public long game_count;

		public int stars;

		public Integer exp_change;
		public Integer exp_now;

		public Double exp_start_ratio;
		public Double exp_change_ratio;
		public Double exp_left_ratio;
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
		public long wins;
		public long wins_gained;

		public long defeats;
		public long defeats_gained;

		public double winrate;
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
		public String key;
		public WinDefeatRate win_defeat_rate;
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
		public ClothingInfo head;
		public ClothingInfo shirt;
		public ClothingInfo shoes;
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
		public String name;
		public String image;
		public int stars;
		public long game_count;

		public String main_image;
		public String sub_1_image;
		public String sub_2_image;
		public String sub_3_image;
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
		public List<TeamData> teams;
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
			WIN, LOSE, SUPPORT
		}

		public Result result;
		public String result_str;
		public String color;

		public List<PlayerData> players;
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
		public String name;
		public boolean is_myself;

		public String weapon_image;
		public String special_weapon_image;
		public String sub_weapon_image;

		public String head_main_image;
		public String shirt_main_image;
		public String shoes_main_image;

		public int kills;
		public int assists;
		public int deaths;
		public int specials;
		public int paint;

		public Long number_of_games;
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
		public String name;
		public String image;

		public List<KeyWinDefeatRate> stats;
	}
}

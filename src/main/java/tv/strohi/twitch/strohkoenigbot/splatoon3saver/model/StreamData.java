package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
public class StreamData {
	private String type;

	private WeaponInfo weapon_info;
	private AbilitiesInfo abilities_info;

	private MatchStats team_stats;
	private StreamStats stream_stats;
	private GameStats game_stats;
	private SpecialStats special_stats;
	private PowerStats power_stats;

	public static StreamData empty() {
		return StreamData.builder().type("NONE").build();
	}

	public static StreamData.StreamDataBuilder prepare() {
		return StreamData.builder().type("STATS");
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class WeaponInfo {
		private String image;
		private String sub_weapon_image;
		private String special_weapon_image;

		private Integer wins;
		private Integer stars;

		private Integer exp_start;
		private Integer exp_change;
		private Integer exp_now;

		private Double exp_start_ratio;
		private Double exp_change_ratio;
		private Double exp_left_ratio;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class AbilitiesInfo {
		private PieceAbilitiesInfo head;
		private PieceAbilitiesInfo shirt;
		private PieceAbilitiesInfo shoes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class PieceAbilitiesInfo {
		private String main_image;
		private String sub_1_image;
		private String sub_2_image;
		private String sub_3_image;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class MatchStats {
		private TeamResult own_team;
		private TeamResult opp_1;
		private TeamResult opp_2;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class TeamResult {
		private String color;
		private String result;
		private String result_points;
		private Long result_ratio;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class StreamStats {
		private Long wins;
		private Long defeats;
		private Double win_ratio;
		private Double defeat_ratio;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class GameStats {
		private Integer kills;
		private Integer deaths;
		private Integer assists;
		private Integer specials;
		private Integer paint;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class SpecialStats {
		private String image;
		private Integer wins;
		private Integer gained;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	@ToString
	@Builder(toBuilder = true)
	public static class PowerStats {
		private String mode_image;
		private String rule_image;

		private Double power_current;
		private Double power_change;
		private Double power_max;
	}
}

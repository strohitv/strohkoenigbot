package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Nameplate;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id.ResultIdTeamOrderPlayerId;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_result_team_player")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(ResultIdTeamOrderPlayerId.class)
public class Splatoon3VsResultTeamPlayer {
	@Id
	@Column(name = "result_id")
	private Long resultId;

	@Id
	@Column(name = "team_order")
	private Integer teamOrder;

	@Id
	@Column(name = "player_id")
	private long playerId;

	private Boolean isMyself;

	private String name;

	private String nameId;

	private String title;

	private String species;

	private Integer paint;

	private Integer kills;

	private Integer deaths;

	private Integer assists;

	private Integer specials;

	private Integer ultraSignalAttempts;

	private Boolean hasCrown;

	private String splatfestDragonCert;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns({
		@JoinColumn(name = "result_id", insertable = false, updatable = false),
		@JoinColumn(name = "team_order", insertable = false, updatable = false)
	})
	@EqualsAndHashCode.Exclude
	private Splatoon3VsResultTeam team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player_id", insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3Player player;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nameplate_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3Nameplate nameplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_left_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3Badge badgeLeft;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_middle_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3Badge badgeMiddle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_right_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3Badge badgeRight;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsWeapon weapon;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsGear headGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_main_ability_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility headGearMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_1_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility headGearSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_2_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility headGearSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_3_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility headGearSecondaryAbility3;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsGear clothingGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_main_ability_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility clothingMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_1_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility clothingSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_2_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility clothingSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_3_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility clothingSecondaryAbility3;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsGear shoesGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_main_ability_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility shoesMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_1_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility shoesSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_2_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility shoesSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_3_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsAbility shoesSecondaryAbility3;
}

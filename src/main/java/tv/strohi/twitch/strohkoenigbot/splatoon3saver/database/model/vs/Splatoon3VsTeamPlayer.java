package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id.TeamPlayerId;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_team_player")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(TeamPlayerId.class)
public class Splatoon3VsTeamPlayer {
	@Id
	@Column(name = "team_id")
	private long teamId;

	@Id
	@Column(name = "player_id")
	private long playerId;

	private Boolean isMyself;

	private String name;

	private Integer nameId;

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
	@JoinColumn(name = "team_id", insertable = false, updatable = false)
	private Splatoon3VsTeam team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player_id", insertable = false, updatable = false)
	private Splatoon3Player player;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nameplate_id")
	private Splatoon3Player nameplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_left_id")
	private Splatoon3Player badgeLeft;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_middle_id")
	private Splatoon3Player badgeMiddle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_right_id")
	private Splatoon3Player badgeRight;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_id")
	private Splatoon3VsWeapon weapon;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_id")
	private Splatoon3VsGear headGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_main_ability_id")
	private Splatoon3VsAbility headGearMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_1_id")
	private Splatoon3VsAbility headGearSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_2_id")
	private Splatoon3VsAbility headGearSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_gear_secondary_ability_3_id")
	private Splatoon3VsAbility headGearSecondaryAbility3;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_id")
	private Splatoon3VsGear clothingGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_main_ability_id")
	private Splatoon3VsAbility clothingMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_1_id")
	private Splatoon3VsAbility clothingSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_2_id")
	private Splatoon3VsAbility clothingSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clothing_gear_secondary_ability_3_id")
	private Splatoon3VsAbility clothingSecondaryAbility3;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_id")
	private Splatoon3VsGear shoesGear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_main_ability_id")
	private Splatoon3VsAbility shoesMainAbility;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_1_id")
	private Splatoon3VsAbility shoesSecondaryAbility1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_2_id")
	private Splatoon3VsAbility shoesSecondaryAbility2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shoes_gear_secondary_ability_3_id")
	private Splatoon3VsAbility shoesSecondaryAbility3;
}

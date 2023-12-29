package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Nameplate;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id.ResultPlayerId;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_result_player")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@IdClass(ResultPlayerId.class)
public class Splatoon3SrResultPlayer {
	@Id
	@Column(name = "result_id")
	private long resultId;

	@Id
	@Column(name = "player_id")
	private long playerId;

	private Boolean isMyself;

	private String name;

	private Integer nameId;

	private String title;

	private String Species;

	private Integer enemiesDefeated;

	private Integer normalEggsDelivered;

	private Integer goldenEggsDelivered;

	private Integer goldenEggsAssisted;

	private Integer rescueCount;

	private Integer rescuedCount;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3SrResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3Player player;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nameplate_id", nullable = false)
	private Splatoon3Nameplate nameplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_left_id", nullable = false)
	private Splatoon3Badge badgeLeft;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_middle_id", nullable = false)
	private Splatoon3Badge badgeMiddle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "badge_right_id", nullable = false)
	private Splatoon3Badge badgeRight;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uniform_id", nullable = false)
	private Splatoon3SrUniform uniform;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "special_weapon_id", nullable = false)
	private Splatoon3SrSpecialWeapon specialWeapon;
}

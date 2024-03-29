package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.Instant;

@Entity(name = "splatoon_2_weapon_request_ranking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2WeaponRequestRanking {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long accountId;

	private String twitchId;

	private String twitchName;

	private Integer winStreak;

	private Long weaponId;

	private Instant challengedAt;
}

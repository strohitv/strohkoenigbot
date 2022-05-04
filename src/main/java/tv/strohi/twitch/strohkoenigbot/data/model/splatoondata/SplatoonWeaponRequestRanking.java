package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonWeaponRequestRanking {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String twitchId;

	private String twitchName;

	private Integer winStreak;

	private Long weaponId;

	private Instant challengedAt;
}

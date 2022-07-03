package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon_2_weapon_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2WeaponStats {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Long weaponId;
	private Long accountId;

	private Long turf;
	private Integer wins;
	private Integer defeats;

	private Double currentFlag;
	private Double maxFlag;
}

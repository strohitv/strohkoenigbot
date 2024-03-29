package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon_2_ability_match")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2AbilityMatch {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long abilityId;
	private Long matchId;
	private Splatoon2GearType kind;
	private Integer gearPosition;
}

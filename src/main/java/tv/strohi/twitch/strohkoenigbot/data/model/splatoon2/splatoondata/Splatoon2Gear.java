package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon_2_gear")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Gear {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String splatoonApiId;

	private Splatoon2GearType kind;

	private String name;

	private String image;
}

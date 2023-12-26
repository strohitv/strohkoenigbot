package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon_2_ability")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Ability {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String splatoonApiId;

	private String name;

	private String image;
}

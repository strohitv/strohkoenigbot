package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon2_weapon")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Weapon {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String splatoonApiId;
	private String name;
	private String image;

	private String subSplatoonApiId;
	private String subName;
	private String subImage;

	private String specialSplatoonApiId;
	private String specialName;
	private String specialImage;

	private Long turf;
	private Integer wins;
	private Integer defeats;
}

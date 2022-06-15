package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_2_stage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Stage {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String splatoonApiId;

	private String name;

	private String image;

	private Integer zonesWins;
	private Integer zonesDefeats;
	private Integer rainmakerWins;
	private Integer rainmakerDefeats;
	private Integer towerWins;
	private Integer towerDefeats;
	private Integer clamsWins;
	private Integer clamsDefeats;
}

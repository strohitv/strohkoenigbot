package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon_2_stage_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2StageStats {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long stageId;
	private Long accountId;

	private Integer zonesWins;
	private Integer zonesDefeats;
	private Integer rainmakerWins;
	private Integer rainmakerDefeats;
	private Integer towerWins;
	private Integer towerDefeats;
	private Integer clamsWins;
	private Integer clamsDefeats;
}

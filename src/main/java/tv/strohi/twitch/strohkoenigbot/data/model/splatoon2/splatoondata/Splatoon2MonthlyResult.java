package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity(name = "splatoon2_monthly_result")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2MonthlyResult {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Integer periodYear;
	private Integer periodMonth;
	private Long startTime;
	private Long endTime;

	private Double zonesCurrent;
	private Double zonesPeak;
	private Long zonesWeaponId;

	private Double rainmakerCurrent;
	private Double rainmakerPeak;
	private Long rainmakerWeaponId;

	private Double towerCurrent;
	private Double towerPeak;
	private Long towerWeaponId;

	private Double clamsCurrent;
	private Double clamsPeak;
	private Long clamsWeaponId;
}

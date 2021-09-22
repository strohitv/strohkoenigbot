package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonPlayerPeaks {
	private double rainmakerPeak = -1;
	private String rainmakerPeakWeaponImage;
	private Integer rainmakerPeakRank;
	private Instant rainmakerMonth;

	private double zonesPeak = -1;
	private String zonesPeakWeaponImage;
	private Integer zonesPeakRank;
	private Instant zonesMonth;

	private double towerPeak = -1;
	private String towerPeakWeaponImage;
	private Integer towerPeakRank;
	private Instant towerMonth;

	private double clamsPeak = -1;
	private String clamsPeakWeaponImage;
	private Integer clamsPeakRank;
	private Instant clamsMonth;
}

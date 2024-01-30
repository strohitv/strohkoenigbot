package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class WaveResults implements Serializable {
	private IdAndName eventWave;
	private List<Weapon> specialWeapons;
	private Integer teamDeliverCount;
	private Integer waterLevel;
	private Integer waveNumber;
	private Integer goldenPopCount;
	private Integer deliverNorm;
}

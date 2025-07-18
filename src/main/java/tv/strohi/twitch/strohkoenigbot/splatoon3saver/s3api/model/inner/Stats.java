package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
public class Stats {
	private String __typename;

	private Integer exp;
	private Long paint;

	// stage stats
	private Double winRateTw;
	private Double winRateAr;
	private Double winRateLf;
	private Double winRateGl;
	private Double winRateCl;

	private String lastUsedTime;
	private Integer level;
	private Integer expToLevelUp;
	private Integer win;
	private Double vibes;

	private Double maxWeaponPower;
	private CurrentWeaponPowerOrder currentWeaponPowerOrder;

	public Instant getLastUsedTimeAsInstant() {
		return Instant.parse(lastUsedTime);
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CurrentWeaponPowerOrder implements Serializable {
		private String id;
		private Double order;
		private Integer weaponPower;
	}
}

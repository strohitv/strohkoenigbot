package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Match implements Serializable {
	private String mode;
	private Integer earnedUdemaePoint;

	private Double lastXPower;
	private BankaraPower bankaraPower;
	private Double weaponPower;

	@Getter
	@Setter
	public static class BankaraPower implements Serializable {
		private Double power;
	}
}

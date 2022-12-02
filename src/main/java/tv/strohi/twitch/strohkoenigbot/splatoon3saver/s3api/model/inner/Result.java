package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class Result implements Serializable {
	private Integer score;
	private Integer noroshi;
	private Double paintRatio;
	private Integer paintPoint;

	private Integer deliverCount;
	private Integer goldenDeliverCount;

	// Salmon Run
	private Player player;
	private Weapon[] weapons;
	private Weapon specialWeapon;

	private Integer defeatEnemyCount;
	private Integer goldenAssistCount;
	private Integer rescueCount;
	private Integer rescuedCount;
}

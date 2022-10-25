package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class CoopResult implements Serializable {
	private Player player;

	private Integer deliverCount;
	private Integer goldenDeliverCount;
	private Integer goldenAssistCount;
	private Integer defeatEnemyCount;
	private Integer rescueCount;
	private Integer rescuedCount;

	private List<NameAndImage> weapons;
	private IdAndNameAndImage specialWeapon;
}

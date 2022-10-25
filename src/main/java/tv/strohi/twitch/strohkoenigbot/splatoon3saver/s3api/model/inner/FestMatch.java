package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class FestMatch implements Serializable {
	private Integer contribution;
	private Integer jewel;
	private String dragonMatchType;
	private Double myFestPower;
}

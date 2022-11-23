package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class GearPower implements Serializable {
	private String name;
	private Image image;
	private String desc;
	private Boolean isEmptySlot;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
public class GearPower implements Serializable {
	private String __typename;
	private String name;
	private Image image;
	private String desc;
	private Boolean isEmptySlot;
}

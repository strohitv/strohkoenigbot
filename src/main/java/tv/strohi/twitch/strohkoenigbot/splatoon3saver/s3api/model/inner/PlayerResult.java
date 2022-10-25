package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class PlayerResult implements Serializable {
	private Integer special;
	private Integer death;
	private Integer assist;
	private Integer noroshiTry;
	private Integer kill;
}

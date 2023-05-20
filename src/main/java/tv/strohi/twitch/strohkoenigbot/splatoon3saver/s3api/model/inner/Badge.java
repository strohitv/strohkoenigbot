package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
// @Accessors(fluent = true)
public class Badge implements Serializable {
	private String id;
	private String description;
	private Image image;
}

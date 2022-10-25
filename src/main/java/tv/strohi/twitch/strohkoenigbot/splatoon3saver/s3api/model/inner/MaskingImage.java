package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class MaskingImage implements Serializable {
	private String maskImageUrl;
	private String overlayImageUrl;
	private Integer width;
	private Integer height;
}

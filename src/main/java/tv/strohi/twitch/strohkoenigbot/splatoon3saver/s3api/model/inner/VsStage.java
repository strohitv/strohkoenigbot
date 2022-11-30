package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class VsStage  implements Serializable {
	private Integer vsStageId;
	private String id;
	private String name;
	private Image image;

	// stage stats
	private Image originalImage;
	private Stats stats;
}

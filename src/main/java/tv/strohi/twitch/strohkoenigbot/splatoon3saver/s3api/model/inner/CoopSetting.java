package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoopSetting {
	private String __typename;
	private String __isCoopSetting;

	private String rule;

	private CoopStage coopStage;
	private NameAndImage[] weapons;
}

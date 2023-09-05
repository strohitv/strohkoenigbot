package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RotationMatchSetting {
	private String __isVsSetting;
	private String __typename;

	private VsStage[] vsStages;
	private VsRule vsRule;

	// not in turf war
	private String mode;

	// only in Anarchy, distinction between series ("CHALLENGE") and open ("OPEN")
	private String bankaraMode;
}

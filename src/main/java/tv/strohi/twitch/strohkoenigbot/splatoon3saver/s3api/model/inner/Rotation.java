package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class Rotation {
	private String startTime;
	private String endTime;

	private RotationMatchSetting regularMatchSetting;
	private RotationMatchSetting[] bankaraMatchSettings;
	@JsonProperty("xMatchSetting")
	private RotationMatchSetting xMatchSetting;
	private RotationMatchSetting leagueMatchSetting;

	private RotationMatchSetting festMatchSetting;

	public Instant getStartTimeAsInstant() {
		return Instant.parse(startTime);
	}

	public Instant getEndTimeAsInstant() {
		return Instant.parse(endTime);
	}
}

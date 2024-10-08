package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Rotation {
	private String __typename;

	private String startTime;
	private String endTime;

	private RotationMatchSetting regularMatchSetting;
	private RotationMatchSetting[] bankaraMatchSettings;
	@JsonProperty("xMatchSetting")
	private RotationMatchSetting xMatchSetting;
	private RotationMatchSetting leagueMatchSetting;

	private RotationMatchSetting festMatchSetting;
	private RotationMatchSetting[] festMatchSettings;

	@JsonIgnore
	public Instant getStartTimeAsInstant() {
		return Instant.parse(startTime);
	}

	@JsonIgnore
	public Instant getEndTimeAsInstant() {
		return Instant.parse(endTime);
	}
}

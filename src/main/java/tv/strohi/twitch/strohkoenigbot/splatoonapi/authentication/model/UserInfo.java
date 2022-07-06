package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
	private String nickname;
	private String country;
	private String birthday;
	private String language;
}

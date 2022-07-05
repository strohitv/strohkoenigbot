package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NsoAppLoginData {
	private NsoLoginResult result;

	@JsonIgnoreProperties(ignoreUnknown = true)
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NsoLoginResult {
		private NsoAccessToken webApiServerCredential;
		private String accessToken;

		@JsonIgnoreProperties(ignoreUnknown = true)
		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class NsoAccessToken {
			private int expiresIn;
			private String accessToken;
		}
	}
}

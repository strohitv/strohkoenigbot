package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NsoAppLoginData {
	private NsoLoginResult result;

	public NsoLoginResult getResult() {
		return result;
	}

	public void setResult(NsoLoginResult result) {
		this.result = result;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class NsoLoginResult {
		private NsoAccessToken webApiServerCredential;

		public NsoAccessToken getWebApiServerCredential() {
			return webApiServerCredential;
		}

		public void setWebApiServerCredential(NsoAccessToken webApiServerCredential) {
			this.webApiServerCredential = webApiServerCredential;
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class NsoAccessToken {
			private int expiresIn;
			private String accessToken;

			public int getExpiresIn() {
				return expiresIn;
			}

			public void setExpiresIn(int expiresIn) {
				this.expiresIn = expiresIn;
			}

			public String getAccessToken() {
				return accessToken;
			}

			public void setAccessToken(String accessToken) {
				this.accessToken = accessToken;
			}
		}
	}
}

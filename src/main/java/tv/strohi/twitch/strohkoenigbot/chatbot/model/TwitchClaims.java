package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TwitchClaims {
	@JsonProperty("id_token")
	private TwitchClaimsIdToken idToken;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TwitchClaimsIdToken {
		@JsonProperty("email")
		private Object email;
		@JsonProperty("email_verified")
		private Object emailVerified;
		@JsonProperty("picture")
		private Object picture;
		@JsonProperty("preferred_username")
		private Object preferredUsername;
		@JsonProperty("updated_at")
		private Object updatedAt;
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TwitchAccessTokenResponse {
	@JsonProperty("access_token")
	private String accessToken;
	@JsonProperty("id_token")
	private String idToken;
	@JsonProperty("refresh_token")
	private String refreshToken;
	@JsonProperty("token_type")
	private String tokenType;
	@JsonProperty("expires_in")
	private Integer expiresIn;
	@JsonProperty("scope")
	private List<String> scope;
	@JsonProperty("nonce")
	private String nonce;
}

package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TwitchIdTokenResponse {
	@JsonProperty("aud")
	private String aud;
	@JsonProperty("azp")
	private String azp;
	@JsonProperty("exp")
	private Long exp;
	@JsonProperty("iat")
	private Long iat;
	@JsonProperty("iss")
	private String iss;
	@JsonProperty("sub")
	private String sub;

	@JsonProperty("email")
	private String email;
	@JsonProperty("email_verified")
	private Boolean emailVerified;
	@JsonProperty("picture")
	private String picture;
	@JsonProperty("preferred_username")
	private String preferredUsername;
	@JsonProperty("updated_at")
	private String updatedAt;

	public ZonedDateTime getUpdatedAtAsDate() {
		if (updatedAt == null) {
			return null;
		}

		return ZonedDateTime.parse(updatedAt);
	}

	public Instant getExpiresAsInstant() {
		return Instant.ofEpochSecond(exp);
	}

	public Instant getIssuedAsInstant() {
		return Instant.ofEpochSecond(iat);
	}
}

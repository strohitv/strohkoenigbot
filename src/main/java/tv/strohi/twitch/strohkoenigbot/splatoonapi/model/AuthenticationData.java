package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationData {
	private String nickname;
	private String cookie;
	private Instant cookieExpiresAt;
	private String sessionToken;
}

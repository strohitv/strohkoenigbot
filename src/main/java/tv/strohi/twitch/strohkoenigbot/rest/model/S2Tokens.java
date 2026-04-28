package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class S2Tokens {
	private String cookie;
	private Instant expiresAt;
	private String nickname;
	private String sessionToken;
}

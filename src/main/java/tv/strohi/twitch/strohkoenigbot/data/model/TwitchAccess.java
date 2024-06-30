package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity(name = "twitch_access")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TwitchAccess {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String accessToken;

	private String idToken;

	private String refreshToken;

	private String scopes;

	private Boolean useForMessages;

	private String userId;

	private String preferredUsername;

	private String picture;

	private String email;

	private Boolean emailVerified;

	private ZonedDateTime updatedAt;

	private Instant expiresAt;

	public List<String> getScopesList() {
		if (scopes == null) {
			return List.of();
		}

		return Arrays.stream(scopes.split(" "))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.collect(Collectors.toList());
	}

	public void setScopes(List<String> scopesList) {
		scopes = scopesList.stream()
			.reduce((a, b) -> String.format("%s %s", a, b))
			.orElse("");
	}
}

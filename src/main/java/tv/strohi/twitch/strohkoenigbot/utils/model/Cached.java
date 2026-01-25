package tv.strohi.twitch.strohkoenigbot.utils.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class Cached<T> {
	private Instant expirationTime;
	private T object;
}

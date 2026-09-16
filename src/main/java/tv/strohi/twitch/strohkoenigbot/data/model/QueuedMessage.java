package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "queued_message")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class QueuedMessage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Instant queuedAt;

	@Lob
	private String message;
}

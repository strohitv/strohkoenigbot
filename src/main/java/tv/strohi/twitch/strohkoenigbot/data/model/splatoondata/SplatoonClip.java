package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonClip {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Long startTime;

	private Long endTime;

	private Boolean isGoodPlay;

	private String clipUrl;

	private String description;

	private Long matchId;

	public Instant getStartTimeAsInstant() {
		return Instant.ofEpochSecond(startTime);
	}

	public Instant getEndTimeAsInstant() {
		return Instant.ofEpochSecond(endTime);
	}
}

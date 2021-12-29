package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonRotation {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Long splatoonApiId;

	private Long startTime;

	private Long endTime;

	private Long stageAId;

	private Long stageBId;

	private SplatoonMode mode;

	private SplatoonRule rule;

	public Instant getStartTimeAsInstant() {
		return Instant.ofEpochSecond(startTime);
	}

	public Instant getEndTimeAsInstant() {
		return Instant.ofEpochSecond(endTime);
	}
}

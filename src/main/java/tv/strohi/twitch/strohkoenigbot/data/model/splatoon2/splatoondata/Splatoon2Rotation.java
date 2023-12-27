package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_2_rotation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Rotation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long splatoonApiId;

	private Long startTime;

	private Long endTime;

	@Column(name = "stage_a_id")
	private Long stageAId;

	@Column(name = "stage_b_id")
	private Long stageBId;

	private Splatoon2Mode mode;

	private Splatoon2Rule rule;

	@JsonIgnore
	public Instant getStartTimeAsInstant() {
		return Instant.ofEpochSecond(startTime);
	}

	@JsonIgnore
	public Instant getEndTimeAsInstant() {
		return Instant.ofEpochSecond(endTime);
	}
}

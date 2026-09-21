package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_vs_gear_chunk_gain")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsGearChunkGain {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant receivedAt;

	private int newExperience;

	private int previousExperience;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "gear_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsGear gear;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_vs_rotation_slot")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsRotationSlot {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant startTime;

	private Instant endTime;

	// ---
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3VsRotation rotation;
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
	private long id;

	private Instant startTime;

	private Instant endTime;

	// ---
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rotation_id", nullable = false, insertable = false, updatable = false)
	private Splatoon3VsRotation rotation;
}

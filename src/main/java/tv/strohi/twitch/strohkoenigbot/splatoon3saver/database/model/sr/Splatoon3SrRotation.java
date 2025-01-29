package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_sr_rotation")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3SrRotation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant startTime;

	private Instant endTime;

	@Lob
	private String shortenedJson;

	// ---
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrStage stage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrMode mode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "boss_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3SrBoss boss;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_1_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrWeapon weapon1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_2_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrWeapon weapon2;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_3_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrWeapon weapon3;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weapon_4_id", nullable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrWeapon weapon4;
}

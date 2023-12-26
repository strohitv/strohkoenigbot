package tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.Splatoon3Mode;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_3_sr_rotation")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3SrRotation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private Long stageId;

	private Long modeId;

	private Long bossId;

	private Instant startTime;

	private Instant endTime;

	private Long weapon1Id;

	private Long weapon2Id;

	private Long weapon3Id;

	private Long weapon4Id;

	private String shortenedJson;

	// ---
	@ManyToOne
	@JoinColumn(name = "stage_id")
	private Splatoon3SrStage stage;

	@ManyToOne
	@JoinColumn(name = "mode_id")
	private Splatoon3Mode mode;

	@ManyToOne
	@JoinColumn(name = "boss_id")
	private Splatoon3SrBoss boss;

	@ManyToOne
	@JoinColumn(name = "weapon_1_id")
	private Splatoon3SrWeapon weapon1;

	@ManyToOne
	@JoinColumn(name = "weapon_2_id")
	private Splatoon3SrWeapon weapon2;

	@ManyToOne
	@JoinColumn(name = "weapon_3_id")
	private Splatoon3SrWeapon weapon3;

	@ManyToOne
	@JoinColumn(name = "weapon_4_id")
	private Splatoon3SrWeapon weapon4;
}

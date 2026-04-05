package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_inksight_player_stats")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "result")
public class Splatoon3VsInksightPlayerStats {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Double power;

	private Double mmr;

	@Column(name = "x_power_sz")
	private Double xPowerZones;

	@Column(name = "x_power_tc")
	private Double xPowerTower;

	@Column(name = "x_power_rm")
	private Double xPowerRain;

	@Column(name = "x_power_cb")
	private Double xPowerClams;

	private Double innerMmr;

	private Double alivePct;

	private Integer playerLevel;

	// ---
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3VsResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player_id")
	@EqualsAndHashCode.Exclude
	private Splatoon3Player player;
}

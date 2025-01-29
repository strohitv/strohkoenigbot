package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_mode_discord_channel")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3SrModeDiscordChannel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id", insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3SrMode mode;

	private String discordChannelName;
}

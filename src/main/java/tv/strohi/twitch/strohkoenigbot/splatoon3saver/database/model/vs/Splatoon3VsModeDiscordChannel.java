package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_mode_discord_channel")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsModeDiscordChannel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mode_id", insertable = false, updatable = false)
	@EqualsAndHashCode.Exclude
	private Splatoon3VsMode mode;

	private String discordChannelName;
}

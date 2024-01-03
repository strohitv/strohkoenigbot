package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_vs_mode")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "discordChannels")
public class Splatoon3VsMode {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String apiId;

	private String apiMode;

	private String apiTypename;

	private String apiBankaraMode;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "mode")
	private List<Splatoon3VsModeDiscordChannel> discordChannels;
}

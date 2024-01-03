package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_sr_mode")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "discordChannels")
public class Splatoon3SrMode {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String apiTypename;

	private String apiMode;

	private String apiRule;

	private String apiSchedulesName;

	// ---

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "mode")
	private List<Splatoon3SrModeDiscordChannel> discordChannels;
}

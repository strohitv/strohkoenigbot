package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_sr_mode")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3SrMode {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String apiTypename;

	private String apiRule;

	private String apiSchedulesName;

	// ---

	@OneToMany
	@JoinColumn(name = "mode_id", insertable = false, updatable = false)
	private List<Splatoon3SrModeDiscordChannel> discordChannels;
}

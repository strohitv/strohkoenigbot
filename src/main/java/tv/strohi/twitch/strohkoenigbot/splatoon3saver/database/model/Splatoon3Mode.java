package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_mode")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3Mode {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiTypename;

	private String apiBankaraMode;

	// ---

	@OneToMany
	@JoinColumn(name = "mode_id", insertable = false, updatable = false)
	private List<Splatoon3ModeDiscordChannel> discordChannels;
}

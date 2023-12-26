package tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_3_mode_discord_channel")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3ModeDiscordChannel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private long modeId;

	private String discordChannelName;
}

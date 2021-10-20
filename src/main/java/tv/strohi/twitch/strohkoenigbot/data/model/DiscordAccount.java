package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscordAccount {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String twitchUserId;

	private Long discordId;

	private Boolean consent;
}

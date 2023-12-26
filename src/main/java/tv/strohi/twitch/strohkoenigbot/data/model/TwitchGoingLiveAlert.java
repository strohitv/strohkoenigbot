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
public class TwitchGoingLiveAlert {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String twitchChannelName;

	private long guildId;

	private long channelId;

	private String notificationMessage;
}

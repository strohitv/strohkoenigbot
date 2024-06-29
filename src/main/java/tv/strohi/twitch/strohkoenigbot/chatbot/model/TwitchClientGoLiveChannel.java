package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import com.github.twitch4j.TwitchClient;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TwitchClientGoLiveChannel {
	private TwitchClient client;
	private String channelName;
	private boolean enable;
}

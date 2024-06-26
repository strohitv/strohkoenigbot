package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAccess;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TwitchAccessInformation {
	private TwitchAccess access;
	private TwitchClient client;
	private OAuth2Credential token;
}

package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import com.github.twitch4j.TwitchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class TwitchMessageSender {
	private TwitchClient botClient;
	private TwitchClient mainAccountClient;

	@Autowired
	public TwitchMessageSender(@Qualifier("botClient") TwitchClient botClient, @Qualifier("mainAccountClient") TwitchClient mainAccountClient) {
		this.botClient = botClient;
		this.mainAccountClient = mainAccountClient;
	}

	public void send(String channel, String message) {
		botClient.getChat().sendMessage(channel, message);
	}

	public void reply(String channel, String message, String nonce, String messageId) {
		botClient.getChat().sendMessage(channel, message, nonce, messageId);
	}
}

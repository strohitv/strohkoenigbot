package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;

@Component
public class TwitchMessageSender {
	private static TwitchMessageSender botTwitchMessageSender;

	public static TwitchMessageSender getBotTwitchMessageSender() {
		return botTwitchMessageSender;
	}

	private TwitchBotClient botClient;

	@Autowired
	public void setBotClient(TwitchBotClient botClient) {
		this.botClient = botClient;
	}

	public TwitchMessageSender() {
		TwitchMessageSender.botTwitchMessageSender = this;
	}

	public void send(String channel, String message) {
		if (botClient.getClient() != null) {
			botClient.getClient().getChat().sendMessage(channel, message);
		}
	}

	public void reply(String channel, String message, String nonce, String messageId) {
		if (botClient.getClient() != null) {
			botClient.getClient().getChat().sendMessage(channel, message, nonce, messageId);
		}
	}

	public void replyPrivate(String channel, String message) {
		if (botClient.getClient() != null) {
			botClient.getClient().getChat().sendPrivateMessage(channel, message);
		}
	}
}

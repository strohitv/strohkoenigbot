package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;

@Component
public class TwitchMessageSender {
	@Getter
	private static TwitchMessageSender botTwitchMessageSender;

	private TwitchBotClient botClient;

	@Autowired
	public void setBotClient(TwitchBotClient botClient) {
		this.botClient = botClient;
	}

	public TwitchMessageSender() {
		TwitchMessageSender.botTwitchMessageSender = this;
	}

	public void send(String channel, String message) {
		if (botClient.getMessageClient() != null) {
			botClient.getMessageClient().getChat().sendMessage(channel, message);
		}
	}

	public void reply(String channel, String message, String nonce, String messageId) {
		if (botClient.getMessageClient() != null) {
			botClient.getMessageClient().getChat().sendMessage(channel, message, nonce, messageId);
		}
	}

	public void replyPrivate(String recipientId, String message) {
		var messageSender = botClient.getMessageConnection();

		if (messageSender != null) {
			messageSender.getClient().getHelix().sendWhisper(null, messageSender.getAccess().getUserId(), recipientId, message).execute();
		}
	}
}

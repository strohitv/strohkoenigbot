package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;

public class TwitchDiscordMessageSender {
	private final TwitchMessageSender messageSender;
	private final DiscordBot discordBot;

	private final TriggerReason messageOrigin;
	private final String twitchChannelName;
	private final String twitchNonce;
	private final String twitchMessageId;
	private final Long discordId;

	public TwitchDiscordMessageSender(TwitchMessageSender messageSender, DiscordBot discordBot, TriggerReason messageOrigin, String twitchChannelName, String twitchNonce, String twitchMessageId, Long discordId) {
		this.messageSender = messageSender;
		this.discordBot = discordBot;
		this.messageOrigin = messageOrigin;
		this.twitchChannelName = twitchChannelName;
		this.twitchNonce = twitchNonce;
		this.twitchMessageId = twitchMessageId;
		this.discordId = discordId;
	}

	public void send(String message) {
		switch (messageOrigin) {
			case ChatMessage:
				messageSender.reply(
						twitchChannelName,
						message,
						twitchNonce,
						twitchMessageId
				);
				break;
			case PrivateMessage:
				messageSender.replyPrivate(twitchChannelName, message);
				break;
			case DiscordPrivateMessage:
				discordBot.sendPrivateMessage(discordId, message);
				break;
			default:
				break;
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.TextChannel;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;

public class TwitchDiscordMessageSender {
	private final TwitchMessageSender messageSender;
	private final DiscordBot discordBot;

	private final ActionArgs args;

	public TwitchDiscordMessageSender(TwitchMessageSender messageSender,
									  DiscordBot discordBot,
									  ActionArgs args) {
		this.messageSender = messageSender;
		this.discordBot = discordBot;
		this.args = args;
	}

	public void send(String message) {
		switch (args.getReason()) {
			case ChatMessage:
				messageSender.reply(
						(String) args.getArguments().get(ArgumentKey.ChannelName),
						message,
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId)
				);
				break;
			case ChannelPointReward:
				messageSender.send((String) args.getArguments().get(ArgumentKey.ChannelName), message);
				break;
			case PrivateMessage:
				messageSender.replyPrivate((String) args.getArguments().get(ArgumentKey.ChannelName), message);
				break;
			case DiscordPrivateMessage:
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), message);
				break;
			case DiscordMessage:
				discordBot.reply(message,
						(TextChannel) args.getArguments().get(ArgumentKey.ChannelObject),
						(Snowflake) args.getArguments().get(ArgumentKey.MessageNonce));
				break;
			default:
				break;
		}
	}
}

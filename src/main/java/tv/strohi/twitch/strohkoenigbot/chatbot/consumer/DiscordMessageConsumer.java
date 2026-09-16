package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import discord4j.core.object.entity.channel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.DiscordMessageEvent;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class DiscordMessageConsumer implements ApplicationListener<DiscordMessageEvent> {
	private final DiscordBot discordBot;
	private final List<IChatAction> botActions;

	@Override
	public void onApplicationEvent(DiscordMessageEvent discordEvent) {
		var event = discordEvent.getEvent();
		var channel = discordEvent.getChannel();
		var author = discordEvent.getAuthor();
		var message = event.getMessage();

		var triggerReason = getTriggerReason(channel);

		var channelName = channel instanceof GuildChannel
			? ((GuildChannel) channel).getName()
			: author.getUsername();

		if (!"stroh.ink#6833".equals(author.getTag())) {
			var content = event.getMessage().getContent();
			if (content.toLowerCase().startsWith("!debug")) {
				// only debug should execute
				if (!DiscordChannelDecisionMaker.isLocalDebug()) {
					return;
				}

				content = content.substring("!debug".length()).trim();
			} else if (content.toLowerCase().startsWith("!all")) {
				// all instances should execute
				content = content.substring("!all".length()).trim();
			} else if (DiscordChannelDecisionMaker.isLocalDebug()) {
				// only prod should execute regular commands
				return;
			}

			log.info("Handling the discord channel message `{}` from user `{}` to channel '{}' from bot instance = {}, debug = {}",
				message,
				author.getTag(),
				channelName,
				ComputerNameEvaluator.getComputerName(),
				DiscordChannelDecisionMaker.isLocalDebug());

			var username = !author.getDiscriminator().equals("0") ? author.getTag() : author.getUsername();

			var args = new ActionArgs();

			args.setReason(triggerReason);
			args.setUser(username);
			args.setUserId(author.getId().asString());
			args.setAdmin(discordEvent.isAdmin());

			args.getArguments().put(ArgumentKey.Event, event);
			args.getArguments().put(ArgumentKey.Message, content);
			args.getArguments().put(ArgumentKey.MessageNonce, message.getId());
			args.getArguments().put(ArgumentKey.MessageObject, message);

			args.getArguments().put(ArgumentKey.ChannelObject, channel);
			args.getArguments().put(ArgumentKey.ChannelName, channelName);
			args.getArguments().put(ArgumentKey.ChannelId, channel.getId().asString());

			args.setReplySender(
				new TwitchDiscordMessageSender(null, discordBot, args)
			);

			botActions.stream()
				.filter(action -> action.getCauses().contains(triggerReason))
				.forEach(action -> action.run(args));
		}
	}

	private TriggerReason getTriggerReason(MessageChannel channel) {
		if (channel instanceof PrivateChannel) {
			return TriggerReason.DiscordPrivateMessage;
		} else if (channel instanceof TextChannel) {
			return TriggerReason.DiscordMessage;
		} else if (channel instanceof NewsChannel) {
			return TriggerReason.DiscordNewsMessage;
		} else if (channel instanceof VoiceChannel) {
			return TriggerReason.DiscordVoiceMessage;
		}

		var message = String.format("Unknown channel type `%s`, could not choose a valid TriggerReason.", channel.getClass().getName());

		for (var adminId : discordBot.getAdminIds()) {
			discordBot.sendPrivateMessage(adminId, String.format("# Did not find a valid TriggerReason for channel type\n%s", message));
		}

		throw new IllegalArgumentException(message);
	}
}

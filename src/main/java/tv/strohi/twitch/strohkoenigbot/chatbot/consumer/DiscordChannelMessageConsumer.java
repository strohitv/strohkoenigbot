package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.TextChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class DiscordChannelMessageConsumer implements Consumer<MessageCreateEvent> {
	private final List<IChatAction> botActions;
	private final DiscordBot discordBot;
	private final TextChannel channel;

	@Override
	public void accept(MessageCreateEvent event) {
		var author = event.getMessage().getAuthor().orElseThrow();
		var message = event.getMessage();

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

			log.info("Handling the discord channel message `{}` from user `{}` to channel '{}' from bot instance = {}, debug = {}", message, author.getTag(), channel.getName(), ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());

			ActionArgs args = new ActionArgs();

			args.setReason(TriggerReason.DiscordMessage);
			args.setUser(author.getTag());
			args.setUserId(author.getId().asString());

			args.getArguments().put(ArgumentKey.Event, event);
			args.getArguments().put(ArgumentKey.Message, content);
			args.getArguments().put(ArgumentKey.MessageNonce, message.getId());
			args.getArguments().put(ArgumentKey.MessageObject, message);

			args.getArguments().put(ArgumentKey.ChannelObject, channel);
			args.getArguments().put(ArgumentKey.ChannelName, channel.getName());
			args.getArguments().put(ArgumentKey.ChannelId, channel.getId().asString());

			args.setReplySender(
				new TwitchDiscordMessageSender(null, discordBot, args)
			);

			botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.DiscordMessage)).forEach(action -> action.run(args));
		}
	}
}

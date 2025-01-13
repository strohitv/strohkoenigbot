package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import discord4j.core.event.domain.message.MessageCreateEvent;
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
public class DiscordPrivateMessageConsumer implements Consumer<MessageCreateEvent> {
	private final List<IChatAction> botActions;
	private final DiscordBot discordBot;

	@Override
	public void accept(MessageCreateEvent event) {
		var author = event.getMessage().getAuthor().orElseThrow();

		if (!"stroh.ink#6833".equals(author.getTag())) {
			var message = event.getMessage().getContent();
			if (message.toLowerCase().startsWith("!debug")) {
				// only debug should execute
				if (!DiscordChannelDecisionMaker.isLocalDebug()) {
					return;
				}

				message = message.substring("!debug".length()).trim();
			} else if (message.toLowerCase().startsWith("!all")) {
				// all instances should execute
				message = message.substring("!all".length()).trim();
			} else if (DiscordChannelDecisionMaker.isLocalDebug()) {
				// only prod should execute regular commands
				return;
			}

			log.info("Handling the discord private message `{}` from user `{}` from bot instance = {}, debug = {}", message, author.getTag(), ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());

			ActionArgs args = new ActionArgs();

			args.setReason(TriggerReason.DiscordPrivateMessage);
			args.setUser(author.getTag());
			args.setUserId(author.getId().asString());

			args.getArguments().put(ArgumentKey.Event, event);
			args.getArguments().put(ArgumentKey.Message, message);

			args.setReplySender(
				new TwitchDiscordMessageSender(null, discordBot, args)
			);

			botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.DiscordPrivateMessage)).forEach(action -> action.run(args));
		}
	}
}

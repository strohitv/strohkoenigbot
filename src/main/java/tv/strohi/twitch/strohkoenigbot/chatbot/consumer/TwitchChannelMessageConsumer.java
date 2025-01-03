package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import lombok.extern.log4j.Log4j2;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class TwitchChannelMessageConsumer implements Consumer<ChannelMessageEvent> {
	private final List<IChatAction> botActions;

	public TwitchChannelMessageConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(ChannelMessageEvent event) {
		var message = event.getMessage();
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

		log.info("Handling the twitch channel message `{}` from user '{}' to channel `{}` from bot instance = {}, debug = {}", message, event.getUser().getName(), event.getMessageEvent().getChannelName().orElse("unknown"), ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());

		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.ChatMessage);
		args.setUser(event.getUser().getName());
		args.setUserId(event.getUser().getId());

		args.getArguments().put(ArgumentKey.Event, event);

		args.getArguments().put(ArgumentKey.ChannelId, event.getMessageEvent().getChannelId());
		args.getArguments().put(ArgumentKey.ChannelName, event.getMessageEvent().getChannelName().orElse(null));

		args.getArguments().put(ArgumentKey.Message, message);
		args.getArguments().put(ArgumentKey.MessageNonce, event.getNonce());
		args.getArguments().put(ArgumentKey.ReplyMessageId, event.getMessageEvent().getMessageId().orElse(event.getEventId()));

		args.setReplySender(
			new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
	}
}

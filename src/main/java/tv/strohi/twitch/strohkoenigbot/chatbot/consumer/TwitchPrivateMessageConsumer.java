package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.common.events.user.PrivateMessageEvent;
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
public class TwitchPrivateMessageConsumer implements Consumer<PrivateMessageEvent> {
	private final List<IChatAction> botActions;

	public TwitchPrivateMessageConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(PrivateMessageEvent event) {
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

		log.info("Handling the twitch private message `{}` from user `{}` from bot instance = {}, debug = {}", message, event.getUser().getName(), ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());

		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.PrivateMessage);
		args.setUser(event.getUser().getName());
		args.setUserId(event.getUser().getId());

		args.getArguments().put(ArgumentKey.Event, event);
		args.getArguments().put(ArgumentKey.Message, message);
		args.getArguments().put(ArgumentKey.ChannelName, event.getUser().getName());
		args.getArguments().put(ArgumentKey.ChannelId, event.getUser().getId());

		args.setReplySender(
			new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
	}
}

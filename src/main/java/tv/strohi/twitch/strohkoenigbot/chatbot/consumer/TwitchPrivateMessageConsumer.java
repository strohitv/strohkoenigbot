package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.TwitchEvent;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class TwitchPrivateMessageConsumer implements ApplicationListener<TwitchEvent> {
	private final List<IChatAction> botActions;

	@Override
	public void onApplicationEvent(@NotNull TwitchEvent twitchEvent) {
		if (!(twitchEvent.getEvent() instanceof PrivateMessageEvent)) {
			return;
		}

		var messageEvent = (PrivateMessageEvent) twitchEvent.getEvent();

		var message = messageEvent.getMessage();
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

		log.info("Handling the twitch private message `{}` from user `{}` from bot instance = {}, debug = {}", message, messageEvent.getUser().getName(), ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());

		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.PrivateMessage);
		args.setUser(messageEvent.getUser().getName());
		args.setUserId(messageEvent.getUser().getId());

		args.getArguments().put(ArgumentKey.Event, messageEvent);
		args.getArguments().put(ArgumentKey.Message, message);
		args.getArguments().put(ArgumentKey.ChannelName, messageEvent.getUser().getName());
		args.getArguments().put(ArgumentKey.ChannelId, messageEvent.getUser().getId());

		args.setReplySender(
			new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;

import java.util.List;
import java.util.function.Consumer;

public class PrivateMessageConsumer implements Consumer<PrivateMessageEvent> {
	private final List<IChatAction> botActions;

	public PrivateMessageConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(PrivateMessageEvent event) {
		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.PrivateMessage);
		args.setUser(event.getUser().getName());
		args.setUserId(event.getUser().getId());

		args.getArguments().put(ArgumentKey.Event, event);
		args.getArguments().put(ArgumentKey.Message, event.getMessage());
		args.getArguments().put(ArgumentKey.ChannelName, event.getUser().getName());
		args.getArguments().put(ArgumentKey.ChannelId, event.getUser().getId());

		args.setReplySender(
			new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
	}
}

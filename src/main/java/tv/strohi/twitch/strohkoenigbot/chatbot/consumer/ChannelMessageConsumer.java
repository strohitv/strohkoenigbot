package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;

import java.util.List;
import java.util.function.Consumer;

public class ChannelMessageConsumer implements Consumer<ChannelMessageEvent> {
	private final List<IChatAction> botActions;

	public ChannelMessageConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(ChannelMessageEvent event) {
		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.ChatMessage);
		args.setUser(event.getUser().getName());
		args.setUserId(event.getUser().getId());

		args.getArguments().put(ArgumentKey.Event, event);

		args.getArguments().put(ArgumentKey.ChannelId, event.getMessageEvent().getChannelId());
		args.getArguments().put(ArgumentKey.ChannelName, event.getMessageEvent().getChannelName().orElse(null));

		args.getArguments().put(ArgumentKey.Message, event.getMessage());
		args.getArguments().put(ArgumentKey.MessageNonce, event.getNonce());
		args.getArguments().put(ArgumentKey.ReplyMessageId, event.getMessageEvent().getMessageId().orElse(event.getEventId()));

		args.setReplySender(
				new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
	}
}

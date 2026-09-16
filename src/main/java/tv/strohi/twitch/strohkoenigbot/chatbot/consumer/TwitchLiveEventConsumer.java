package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.TwitchEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.TwitchLiveEvent;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TwitchLiveEventConsumer implements ApplicationListener<TwitchEvent> {
	private final List<IChatAction> botActions;

	@Override
	public void onApplicationEvent(@NotNull TwitchEvent twitchEvent) {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			return;
		}

		if (!(twitchEvent.getEvent() instanceof TwitchLiveEvent)) {
			return;
		}

		var liveEvent = (TwitchLiveEvent) twitchEvent.getEvent();

		var args = new ActionArgs();

		args.setReason(TriggerReason.LiveStatus);
		args.getArguments().put(ArgumentKey.Event, liveEvent);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.LiveStatus)).forEach(action -> action.run(args));
	}
}

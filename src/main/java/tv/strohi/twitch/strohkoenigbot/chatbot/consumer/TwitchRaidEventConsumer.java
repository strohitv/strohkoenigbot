package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.chat.events.channel.RaidEvent;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.TwitchEvent;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TwitchRaidEventConsumer implements ApplicationListener<TwitchEvent> {
	private final List<IChatAction> botActions;

	@Override
	public void onApplicationEvent(@NotNull TwitchEvent twitchEvent) {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			return;
		}

		if (!(twitchEvent.getEvent() instanceof RaidEvent)) {
			return;
		}

		var raidEvent = (RaidEvent) twitchEvent.getEvent();

		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.Raid);
		args.setUser(raidEvent.getRaider().getName());
		args.setUserId(raidEvent.getRaider().getId());

		args.getArguments().put(ArgumentKey.Event, raidEvent);

		args.getArguments().put(ArgumentKey.ChannelId, raidEvent.getChannel().getId());
		args.getArguments().put(ArgumentKey.ChannelName, raidEvent.getChannel().getName());

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.Raid)).forEach(action -> action.run(args));
	}
}

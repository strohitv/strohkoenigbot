package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.chat.events.channel.RaidEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;
import java.util.function.Consumer;

public class TwitchRaidEventConsumer implements Consumer<RaidEvent> {
	private final List<IChatAction> botActions;

	public TwitchRaidEventConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(RaidEvent raidEvent) {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			return;
		}

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

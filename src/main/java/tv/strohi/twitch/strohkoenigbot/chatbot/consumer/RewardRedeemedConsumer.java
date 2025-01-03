package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;
import java.util.function.Consumer;

public class RewardRedeemedConsumer implements Consumer<RewardRedeemedEvent> {
	private final List<IChatAction> botActions;

	public RewardRedeemedConsumer(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	@Override
	public void accept(RewardRedeemedEvent pointEvent) {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			return;
		}

		ActionArgs args = new ActionArgs();

		args.setReason(TriggerReason.ChannelPointReward);
		args.setUser(pointEvent.getRedemption().getUser().getDisplayName());
		args.setUserId(pointEvent.getRedemption().getUser().getId());

		args.getArguments().put(ArgumentKey.Event, pointEvent);
		args.getArguments().put(ArgumentKey.RewardName, pointEvent.getRedemption().getReward().getTitle());
		args.getArguments().put(ArgumentKey.Message, pointEvent.getRedemption().getUserInput());

		args.getArguments().put(ArgumentKey.ChannelId, pointEvent.getRedemption().getChannelId());

		args.setReplySender(
			new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
		);

		botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChannelPointReward)).forEach(action -> action.run(args));
	}
}

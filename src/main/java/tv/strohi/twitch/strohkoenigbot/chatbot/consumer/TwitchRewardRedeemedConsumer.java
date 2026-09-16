package tv.strohi.twitch.strohkoenigbot.chatbot.consumer;

import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import lombok.RequiredArgsConstructor;
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
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TwitchRewardRedeemedConsumer implements ApplicationListener<TwitchEvent> {
	private final List<IChatAction> botActions;

	@Override
	public void onApplicationEvent(@NotNull TwitchEvent twitchEvent) {
		if (DiscordChannelDecisionMaker.isLocalDebug()) {
			return;
		}

		if (!(twitchEvent.getEvent() instanceof RewardRedeemedEvent)) {
			return;
		}

		var pointEvent = (RewardRedeemedEvent) twitchEvent.getEvent();

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

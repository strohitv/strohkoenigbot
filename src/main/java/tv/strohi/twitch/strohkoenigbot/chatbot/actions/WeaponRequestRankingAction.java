package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;

import java.util.EnumSet;

@Component
public class WeaponRequestRankingAction implements IChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage);
	}

	@Override
	public void run(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!wr intro")) {
			args.getReplySender().send("To make weapon requests more interesting, there's a ranking of which request made me get the biggest win streak! Try giving me a weapon which makes me win many games to reach first place! Type \"!wr list\" in chat to see the ranking.");
		} else if (message.startsWith("!wr list")) {
			args.getReplySender().send("Ranking hasn't started yet and is still todo, sorry...");
		} else if (message.startsWith("!wr rules")) {
			args.getReplySender().send("1. No requests while I'm playing with my Comp team. 2. No requests while I'm doing placements. 3. I'll play your weapon until I lose with it. 4. Banned weapons: Neo Sploosh & Custom Eliter 4k Scope. 5. One request per hour, one user can only do one request per stream.");
		}
	}
}

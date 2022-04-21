package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;

import java.util.EnumSet;

public class StatsAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public interface IChatAction {
	EnumSet<TriggerReason> getCauses();

	void run(ActionArgs args);
}

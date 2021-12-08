package tv.strohi.twitch.strohkoenigbot.chatbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;

import java.util.List;

@Component
public class BotActions {
	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		TwitchChatBot.setBotActions(botActions);
	}
}

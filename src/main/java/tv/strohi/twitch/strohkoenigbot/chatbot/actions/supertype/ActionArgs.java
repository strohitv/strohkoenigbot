package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import lombok.Getter;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ActionArgs {
	private final Map<ArgumentKey, Object> arguments = new HashMap<>();

	@Setter
	private TriggerReason reason;
	@Setter
	private String user;
	@Setter
	private String userId;

	@Setter
	private TwitchDiscordMessageSender replySender;

}

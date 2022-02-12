package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;

import java.util.HashMap;
import java.util.Map;

public class ActionArgs {
	private final Map<ArgumentKey, Object> arguments = new HashMap<>();

	private TriggerReason reason;
	private String user;
	private String userId;

	private TwitchDiscordMessageSender replySender;

	public Map<ArgumentKey, Object> getArguments() {
		return arguments;
	}

	public TriggerReason getReason() {
		return reason;
	}

	public void setReason(TriggerReason reason) {
		this.reason = reason;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public TwitchDiscordMessageSender getReplySender() {
		return replySender;
	}

	public void setReplySender(TwitchDiscordMessageSender replySender) {
		this.replySender = replySender;
	}
}

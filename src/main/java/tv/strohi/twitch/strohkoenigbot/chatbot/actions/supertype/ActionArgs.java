package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import java.util.HashMap;
import java.util.Map;

public class ActionArgs {
	private final Map<ArgumentKey, Object> arguments = new HashMap<>();

	private TriggerReason reason;
	private String user;
	private String userId;

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
}

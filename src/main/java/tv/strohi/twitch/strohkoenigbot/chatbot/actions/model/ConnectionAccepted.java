package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

@FunctionalInterface
public interface ConnectionAccepted {
	void accept(long discordId);
}

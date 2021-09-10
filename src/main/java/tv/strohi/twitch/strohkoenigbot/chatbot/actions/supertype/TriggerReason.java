package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import java.util.EnumSet;

public enum TriggerReason {
	ChatMessage,
	PrivateMessage,
	ChannelPointReward,
	Host,
	Raid,
	Follow;

	public static final EnumSet<TriggerReason> All = EnumSet.allOf(TriggerReason.class);
}

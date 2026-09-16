package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import java.util.EnumSet;

public enum TriggerReason {
	// Twitch
	ChatMessage,
	PrivateMessage,
	ChannelPointReward,
	Host,
	Raid,
	Follow,
	LiveStatus,

	// Discord
	DiscordMessage,
	DiscordPrivateMessage,
	DiscordNewsMessage,
	DiscordVoiceMessage;

	public static final EnumSet<TriggerReason> All = EnumSet.allOf(TriggerReason.class);

	public static final EnumSet<TriggerReason> Twitch = EnumSet.of(
		TriggerReason.ChatMessage,
		TriggerReason.PrivateMessage,
		TriggerReason.ChannelPointReward,
		TriggerReason.Host,
		TriggerReason.Raid,
		TriggerReason.Follow,
		TriggerReason.LiveStatus);

	public static final EnumSet<TriggerReason> Discord = EnumSet.of(
		TriggerReason.DiscordMessage,
		TriggerReason.DiscordPrivateMessage,
		TriggerReason.DiscordNewsMessage,
		TriggerReason.DiscordVoiceMessage);
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import discord4j.core.object.entity.channel.TextChannel;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchGoingLiveAlert;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchGoingLiveAlertRepository;

import java.util.Arrays;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class ManageTwitchGoingLiveNotificationAction extends ChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordMessage);
	}

	private final TwitchGoingLiveAlertRepository twitchGoingLiveAlertRepository;

	private final DiscordBot discordBot;

	private final TwitchBotClient twitchBotClient;

	@EventListener(ApplicationReadyEvent.class)
	public void setUpTwitchBotClient() {
		twitchBotClient.addGoingLiveAlertConsumer(this::sendGoingLiveNotifications);
	}

	@Override
	public void execute(ActionArgs args) {
		var channel = (TextChannel) args.getArguments().get(ArgumentKey.ChannelObject);

		var guild = channel.getGuild().block();
		if (guild == null) return;

		// access management
		if (!"strohkoenig#8058".equals(args.getUser())) {
			var owner = guild.getOwner().block();
			if (owner == null) return;
			var ownerTag = owner.getTag();
			if (!args.getUser().equals(ownerTag)) return;
		}

		var message = (String) args.getArguments().get(ArgumentKey.Message);
		final var prefix = "!twitch alert";
		if (message.startsWith(prefix)) {

			// add going live message
			message = message.substring(prefix.length()).trim();

			if (message.startsWith("notify ")) {
				message = message.substring("notify ".length()).trim();

				String twitchUser = Arrays.stream(message.split("\n")).findFirst().orElse(null);
				String notificationMessage = Arrays.stream(message.split("\n", 2)).skip(1).findFirst().orElse(null);
				if (twitchUser == null || twitchUser.length() == 0 || notificationMessage == null || notificationMessage.trim().length() == 0) {
					args.getReplySender().send("Please tell me a twitch channel and a message for the alert!\n" +
							"The message has to start in a separate line.");
					return;
				} else if (twitchUser.length() > 25 || twitchUser.length() < 3) {
					args.getReplySender().send("Twitch channel name has to be between 3 and 25 characters long.");
					return;
				}

				var twitchUserLC = twitchUser.toLowerCase();

				var alert = twitchGoingLiveAlertRepository
						.findByTwitchChannelNameAndGuildIdAndChannelId(twitchUserLC, guild.getId().asLong(), channel.getId().asLong())
						.stream()
						.findFirst()
						.orElse(new TwitchGoingLiveAlert(0, twitchUserLC, guild.getId().asLong(), channel.getId().asLong(), notificationMessage));

				alert.setNotificationMessage(notificationMessage);

				twitchGoingLiveAlertRepository.save(alert);

				twitchBotClient.enableGoingLiveEvent(twitchUserLC);

				args.getReplySender().send(String.format("Alright! Gonna send a notification to this channel whenever `%s` goes live on Twitch!", twitchUser));
			} else if (message.startsWith("remove ")) {
				message = message.substring("remove ".length()).trim();

				if (message.length() == 0) {
					args.getReplySender().send("Please tell me the twitch channel you don't want to get notified about anymore!");
					return;
				} else if (message.length() > 25 || message.length() < 3) {
					args.getReplySender().send("Twitch channel name has to be between 3 and 25 characters long.");
					return;
				}

				var twitchUserLC = message.toLowerCase();

				var alert = twitchGoingLiveAlertRepository
						.findByTwitchChannelNameAndGuildIdAndChannelId(twitchUserLC, guild.getId().asLong(), channel.getId().asLong())
						.stream()
						.findFirst()
						.orElse(null);

				if (alert != null) {
					twitchGoingLiveAlertRepository.delete(alert);

					var allAlertsForChannel = twitchGoingLiveAlertRepository.findByTwitchChannelName(twitchUserLC);
					if (allAlertsForChannel.size() == 0) {
						twitchBotClient.disableGoingLiveEvent(twitchUserLC);
					}

					args.getReplySender().send(String.format("Alright! I'm not gonna send notifications to this channel anymore whenever `%s` goes live on Twitch!", message));
				} else {
					args.getReplySender().send(String.format("There is no alert for `%s` in this channel...", message));
				}
			} else {
				args.getReplySender().send("Sorry, but I don't understand what you're trying to do.\n" +
						"You can either use `notify` to add a new channel or `remove` to remove an existing notification.");
			}
		}
	}

	private void sendGoingLiveNotifications(String channel) {
		var allAlerts = twitchGoingLiveAlertRepository.findByTwitchChannelName(channel);

		for (var alert : allAlerts) {
			discordBot.sendServerMessageWithImages(alert.getGuildId(), alert.getChannelId(), alert.getNotificationMessage());
		}
	}
}

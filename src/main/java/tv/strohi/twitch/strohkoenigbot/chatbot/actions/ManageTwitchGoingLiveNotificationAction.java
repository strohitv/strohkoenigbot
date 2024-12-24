package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import com.github.twitch4j.events.ChannelGoLiveEvent;
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
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchGoingLiveAlertFilter;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchGoingLiveAlertRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

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
		if (!Long.toString(DiscordBot.ADMIN_ID).equals(args.getUserId())) {
			var owner = guild.getOwner().block();
			if (owner == null) return;
			var ownerTag = owner.getId().asString();
			if (!args.getUserId().equals(ownerTag)) return;
		}

		var message = (String) args.getArguments().get(ArgumentKey.Message);
		final var prefix = "!twitch alert";
		if (message.startsWith(prefix)) {

			// add going live message
			message = message.substring(prefix.length()).trim();

			if (message.startsWith("notify ")) {
				message = message.substring("notify ".length()).trim();

				String twitchUser = Arrays.stream(message.split("\n")).findFirst().orElse(null);

				String notificationMessage = Arrays.stream(message.split("\n"))
					.filter(m -> !m.startsWith("+title"))
					.skip(1)
					.reduce((a, b) -> String.format("%s\n%s", a, b))
					.orElse(null);

				if (twitchUser == null || twitchUser.isEmpty() || notificationMessage == null || notificationMessage.trim().isEmpty()) {
					args.getReplySender().send("Please tell me a twitch channel and a message for the alert!\n" +
						"The message has to start in a separate line.");
					return;
				} else if (twitchUser.length() > 25 || twitchUser.length() < 3) {
					args.getReplySender().send("Twitch channel name has to be between 3 and 25 characters long.");
					return;
				}

				var twitchUserLC = twitchUser.toLowerCase();

				var filters = new TwitchGoingLiveAlertFilter();

				var allLines = message.split("\n");
				Arrays.stream(allLines).forEach(l -> {
					if (l.startsWith("+title")) {
						filters.getIncludeFilters().add(l.substring("+title".length()).trim());
					}
				});

				var alert = twitchGoingLiveAlertRepository
					.findByTwitchChannelNameAndGuildIdAndChannelId(twitchUserLC, guild.getId().asLong(), channel.getId().asLong())
					.stream()
					.findFirst()
					.orElse(new TwitchGoingLiveAlert(0, twitchUserLC, guild.getId().asLong(), channel.getId().asLong(), notificationMessage, null));

				alert.setFilters(filters);
				alert.setNotificationMessage(notificationMessage);

				twitchGoingLiveAlertRepository.save(alert);

				twitchBotClient.enableGoingLiveEvent(twitchUserLC);

				args.getReplySender().send(String.format("Alright! Gonna send a notification to this channel whenever `%s` goes live on Twitch!", twitchUser));
			} else if (message.startsWith("remove ")) {
				message = message.substring("remove ".length()).trim();

				if (message.isEmpty()) {
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
					if (allAlertsForChannel.isEmpty()) {
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

	private void sendGoingLiveNotifications(ChannelGoLiveEvent event) {
		var allAlerts = twitchGoingLiveAlertRepository.findByTwitchChannelName(event.getChannel().getName());

		for (var alert : allAlerts) {
			var filters = alert.getFiltersAsObject();
			if (filtersMatch(filters, event)) {
				if (!DiscordChannelDecisionMaker.isLocalDebug()) {
					discordBot.sendServerMessageWithImageUrls(
						alert.getGuildId(),
						alert.getChannelId(),
						String.format("## *%s*: %s\n%s\n- Game: %s", event.getStream().getUserName(), event.getStream().getTitle(), alert.getNotificationMessage(), event.getStream().getGameName()));
				}
			} else {
				discordBot.sendServerMessageWithImageUrls(
					DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("## *%s*: %s\n%s\n- Game: %s", event.getStream().getUserName(), event.getStream().getTitle(), alert.getNotificationMessage(), event.getStream().getGameName()));
			}
		}
	}

	private boolean filtersMatch(TwitchGoingLiveAlertFilter filters, ChannelGoLiveEvent event) {
		return filters.getIncludeFilters().isEmpty() || filters.getIncludeFilters().stream().anyMatch(f -> event.getStream().getTitle().contains(f));
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOfferNotification;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearShopOfferNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3NotifyForGearShopOffersAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private final DiscordAccountLoader discordAccountLoader;
	private final Splatoon3VsGearShopOfferNotificationRepository shopOfferNotificationRepository;

	@Override
	protected void execute(ActionArgs args) {
		var sender = args.getReplySender();

		var message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		var lowerCaseMessage = message.toLowerCase().trim();

		if (lowerCaseMessage.startsWith("!shops ")) {
			message = message.substring("!shops ".length()).trim();
			lowerCaseMessage = lowerCaseMessage.substring("!shops ".length()).trim();
		} else {
			return;
		}

		var account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

		if (lowerCaseMessage.startsWith("notify")) {
			message = message.substring("notify ".length()).trim();
			addNotification(sender, message, account);
		} else if (lowerCaseMessage.startsWith("notifications")) {
			lowerCaseMessage = lowerCaseMessage.substring("notifications".length()).trim();

			if (lowerCaseMessage.isBlank()) {
				var allNotifications = shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId());

				if (allNotifications.isEmpty()) {
					sender.send("**ERROR**! You don't have any notifications yet");
					return;
				}

				var responseBuilder = new StringBuilder("## Current notifications");

				for (var notification : allNotifications) {
					fillNotificationIntoStringBuilder(notification, responseBuilder);
				}

				responseBuilder.append("\n\nTo receive detailed information about one of them, use **!shops notifications <id>**.");

				sender.send(responseBuilder.toString());
			} else {
				try {
					lowerCaseMessage = lowerCaseMessage.split("\\s")[0];
					var id = Integer.parseInt(lowerCaseMessage);

					var foundNotification = shopOfferNotificationRepository.findByIdAndAccountId(id, account.getId());

					if (foundNotification.isPresent()) {
						StringBuilder responseBuilder = new StringBuilder("## Shop Notification\n");
						fillNotificationIntoStringBuilder(foundNotification.get(), responseBuilder);

						sender.send(responseBuilder.toString());
					} else {
						sender.send(String.format("**ERROR**! Sorry, you don't have a notification with number **%s**", lowerCaseMessage));
					}
				} catch (NumberFormatException ignored) {
					sender.send(String.format("**ERROR**! Whatever you're trying to do, **%s** is not a number!", lowerCaseMessage));
				}
			}

		} else if (lowerCaseMessage.startsWith("clear")) {
			var foundNotifications = shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId());
			shopOfferNotificationRepository.deleteAll(foundNotifications);

			sender.send("## I deleted all your notifications as requested.");
		} else if (lowerCaseMessage.startsWith("delete")) {
			lowerCaseMessage = lowerCaseMessage.substring("delete".length()).trim();

			if (!lowerCaseMessage.isBlank()) {
				String[] idStrings = lowerCaseMessage.split("\\s");

				List<Long> ids = new ArrayList<>();

				for (String idString : idStrings) {
					try {
						ids.add(Long.parseLong(idString));
					} catch (NumberFormatException ignored) {
					}
				}

				var foundNotifications = shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId()).stream()
					.filter(fn -> ids.contains(fn.getId()))
					.collect(Collectors.toList());

				if (!foundNotifications.isEmpty()) {
					shopOfferNotificationRepository.deleteAll(foundNotifications);

					var responseBuilder = new StringBuilder("## I deleted the following notifications:");

					for (var notification : foundNotifications) {
						fillNotificationIntoStringBuilder(notification, responseBuilder);
					}

					sender.send(responseBuilder.toString());
				} else {
					sender.send("**ERROR**! I could not find any notification for the numbers you gave me.");
				}
			} else {
				sender.send("**ERROR**! Please provide at least one number to delete.");
			}
		} else {
			// no valid commands
			sender.send("## Allowed commands\n  - !shops notify\n  - !shops notifications\n  - !shops notifications <id>\n  - !shops clear\n  - !shops delete <id>");
		}
	}

	private void addNotification(TwitchDiscordMessageSender sender, String gearName, Account account) {
		if (shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId()).size() >= 12) {
			sender.send("**ERROR**! You already have 12 notifications for this game mode. Remove some old ones first before adding a new one!");
			return;
		}

		var addedNotification = shopOfferNotificationRepository.save(Splatoon3VsGearShopOfferNotification.builder()
			.account(account)
			.gearName(gearName)
			.build());

		var responseBuilder = new StringBuilder("## The following notification has been added:\n");
		fillNotificationIntoStringBuilder(addedNotification, responseBuilder);

		responseBuilder.append("\n\nI'm gonna send you a private message as soon as this gear appears in the shop.");
		sender.send(responseBuilder.toString());
	}

	private void fillNotificationIntoStringBuilder(Splatoon3VsGearShopOfferNotification notification, StringBuilder builder) {
		builder.append("\n- Id: __")
			.append(notification.getId())
			.append("__ - Gear Name: __")
			.append(notification.getGearName())
			.append("__ ");
	}
}

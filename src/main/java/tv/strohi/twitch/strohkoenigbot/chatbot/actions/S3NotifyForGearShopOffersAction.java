package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOffer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOfferNotification;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearShopOfferNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearShopOfferRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3NotifyForGearShopOffersAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private final DiscordAccountLoader discordAccountLoader;

	private final Splatoon3VsGearRepository gearRepository;
	private final Splatoon3VsGearShopOfferRepository shopOfferRepository;
	private final Splatoon3VsGearShopOfferNotificationRepository shopOfferNotificationRepository;

	@Override
	@Transactional
	public void execute(ActionArgs args) {
		var sender = args.getReplySender();

		var message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		var lowerCaseMessage = message.toLowerCase().trim();

		if (lowerCaseMessage.startsWith("!shops ")) {
			message = message.substring("!shops ".length()).trim();
			lowerCaseMessage = lowerCaseMessage.substring("!shops ".length()).trim();
		} else if (lowerCaseMessage.startsWith("!shop ")) {
			message = message.substring("!shop ".length()).trim();
			lowerCaseMessage = lowerCaseMessage.substring("!shop ".length()).trim();
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

				var list = new ArrayList<NotificationOffer>();

				allNotifications.forEach(n -> {
					var gear = gearRepository.findByName(n.getGearName());
					if (gear.isPresent()) {
						list.add(new NotificationOffer(n,
							shopOfferRepository.findTop5ByGearAndAddedAtAfter(gear.get(),
								LocalDate.now().atStartOfDay().minusSeconds(1L).toInstant(ZoneOffset.UTC)).stream().findFirst().orElse(null)));
					} else {
						list.add(new NotificationOffer(n, null));
					}
				});

				for (var n : list.stream()
					.sorted((a, b) -> sortNo(a.offer, b.offer))
					.collect(Collectors.toList())) {

					fillNotificationIntoStringBuilder(n.notification, responseBuilder);

					var gear = gearRepository.findByName(n.notification.getGearName());
					if (gear.isEmpty()) {
						responseBuilder.append(" - **WARNING** You don't own a gear with this name!");
					} else {
						responseBuilder
							.append(" - `")
							.append(gear.get().getGearLevel())
							.append("` stars")
							.append(" - Next occurrence: ");

						var nextNotification = shopOfferRepository.findTop5ByGearAndAddedAtAfter(gear.get(), Instant.now()).stream().findFirst();
						nextNotification.ifPresent(notif -> responseBuilder
							.append("<t:")
							.append(notif.getAddedAt().getEpochSecond())
							.append(":f> (<t:")
							.append(notif.getAddedAt().getEpochSecond())
							.append(":R>)"));
					}
				}

				responseBuilder.append("\n\nTo receive detailed information about one of them, use **!shops notifications <id>**.");

				sender.send(responseBuilder.toString());
			} else {
				if (!lowerCaseMessage.matches("^[0-9]+$")) {
					sender.send("## ERROR: id must be a number");
					return;
				}

				var id = Long.parseLong(lowerCaseMessage);
				var foundNotificationOptional = shopOfferNotificationRepository.findById(id);

				if (foundNotificationOptional.isPresent()) {
					var foundNotification = foundNotificationOptional.get();

					var responseBuilder = new StringBuilder("## Notification `")
						.append(id)
						.append("`\n");

					fillNotificationIntoStringBuilder(foundNotification, responseBuilder);

					var gear = gearRepository.findByName(foundNotification.getGearName());
					if (gear.isPresent()) {
						responseBuilder
							.append("\n- Current level: `")
							.append(gear.get().getGearLevel())
							.append("`")
							.append("\n\n### Next occurrences in shop");

						var nextNotifications = shopOfferRepository.findTop5ByGearAndAddedAtAfter(gear.get(), Instant.now());
						for (var notification : nextNotifications) {
							responseBuilder
								.append("\n- <t:")
								.append(notification.getAddedAt().getEpochSecond())
								.append(":f> (<t:")
								.append(notification.getAddedAt().getEpochSecond())
								.append(":R>)");
						}
					} else {
						responseBuilder.append("\n\n**WARNING** You don't own a gear with this name!");
					}

					sender.send(responseBuilder.toString());
				} else {
					sender.send("## ERROR: notification could not be found");
				}
			}

		} else if (lowerCaseMessage.startsWith("clear")) {
			var foundNotifications = shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId());
			shopOfferNotificationRepository.deleteAll(foundNotifications);

			sender.send("## I deleted all your notifications as requested.");
		} else if (lowerCaseMessage.startsWith("next")) {
			message = message.substring("next".length()).trim();

			var offerLimit = 20L;

			if (message.matches("^[0-9]+$")) {
				offerLimit = Long.parseLong(message);

				if (offerLimit < 1) {
					offerLimit = 1L;
				} else if (offerLimit > 100) {
					offerLimit = 100L;
				}
			} else {
				sender.send("## ERROR: count of next offers to list must be a number... Switching to the default of `20`...");
			}

			var allNotifications = shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId());

			if (allNotifications.isEmpty()) {
				sender.send("## ERROR: you don't have any notifications...");
				return;
			}

			var allGears = allNotifications.stream()
				.map(Splatoon3VsGearShopOfferNotification::getGearName)
				.distinct()
				.map(gearRepository::findByName)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());

			var allOffers = shopOfferRepository.findTop100ByGearInAndAddedAtAfterOrderByAddedAt(allGears, Instant.now())
				.stream()
				.limit(offerLimit)
				.collect(Collectors.toList());

			if (allOffers.isEmpty()) {
				sender.send("## ERROR: I couldn't find any of the gears you're waiting for in any shops...");
				return;
			}

			var messageBuilder = new StringBuilder("## Here are the next `")
				.append(offerLimit)
				.append("` occurrences of items you're waiting for in shops");

			for (int i = 0; i < allOffers.size(); i++) {
				var offer = allOffers.get(i);

				messageBuilder
					.append("\n")
					.append(i + 1)
					.append(". Id: __")
					.append(allNotifications.stream()
						.filter(n -> n.getGearName().equalsIgnoreCase(offer.getGear().getName()))
						.findFirst()
						.map(Splatoon3VsGearShopOfferNotification::getId)
						.map(Object::toString)
						.orElse("<UNKNOWN ID>"))
					.append("__ - ")
					.append("Gear Name: __")
					.append(offer.getGear().getName())
					.append("__: <t:")
					.append(offer.getAddedAt().getEpochSecond())
					.append(":f> (<t:")
					.append(offer.getAddedAt().getEpochSecond())
					.append(":R>)");
			}

			sender.send(messageBuilder.toString());
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

					var responseBuilder = new StringBuilder("## I deleted the following notifications");

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
			sender.send("## Allowed commands\n  - !shops notify\n  - !shops next <number>\n  - !shops notifications\n  - !shops notifications <id>\n  - !shops clear\n  - !shops delete <id>");
		}
	}

	private void addNotification(TwitchDiscordMessageSender sender, String gearName, Account account) {
		if (shopOfferNotificationRepository.findAllByAccountIdOrderById(account.getId()).size() >= 120) {
			sender.send("**ERROR**! You already have 120 notifications for this game mode. Remove some old ones first before adding a new one!");
			return;
		}

		var addedNotification = shopOfferNotificationRepository.save(Splatoon3VsGearShopOfferNotification.builder()
			.account(account)
			.gearName(gearName)
			.build());

		var responseBuilder = new StringBuilder("## The following notification has been added\n");
		fillNotificationIntoStringBuilder(addedNotification, responseBuilder);

		var gear = gearRepository.findByName(gearName);
		if (gear.isEmpty()) {
			responseBuilder.append("\n\n**WARNING** You don't own a gear with this name!");
		} else {
			responseBuilder.append("\n\n### Next occurrences in shop");

			var nextNotifications = shopOfferRepository.findTop5ByGearAndAddedAtAfter(gear.get(), Instant.now());
			for (var notification : nextNotifications) {
				responseBuilder
					.append("\n- <t:")
					.append(notification.getAddedAt().getEpochSecond())
					.append(":f> (<t:")
					.append(notification.getAddedAt().getEpochSecond())
					.append(":R>)");
			}
		}

		responseBuilder.append("\n\nI'm gonna send you a private message as soon as this gear appears in the shop.");
		sender.send(responseBuilder.toString());
	}

	private void fillNotificationIntoStringBuilder(Splatoon3VsGearShopOfferNotification notification, StringBuilder builder) {
		builder.append("\n- Id: __")
			.append(notification.getId())
			.append("__ - Gear Name: __")
			.append(notification.getGearName())
			.append("__");
	}

	private int sortNo(Splatoon3VsGearShopOffer a, Splatoon3VsGearShopOffer b) {
		if (a == null && b != null) {
			return 1;
		} else if (a != null && b == null) {
			return -1;
		} else if (a == null && b == null) {
			return 0;
		} else {
			return a.getAddedAt().compareTo(b.getAddedAt());
		}
	}

	@AllArgsConstructor
	@Getter
	@Setter
	private static class NotificationOffer {
		private Splatoon3VsGearShopOfferNotification notification;
		private Splatoon3VsGearShopOffer offer;
	}
}

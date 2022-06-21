package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.DayFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunRandomFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunStage;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunWeapon;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.RegexUtils;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TextFilters;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2SalmonRunRotationNotification;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.Splatoon2SalmonRunRotationNotificationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotifyForSalmonRunRotationsAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private TextFilters textFilters;

	@Autowired
	public void setTextFilters(TextFilters textFilters) {
		this.textFilters = textFilters;
	}

	private RegexUtils regexUtils;

	@Autowired
	public void setRegexUtils(RegexUtils regexUtils) {
		this.regexUtils = regexUtils;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2SalmonRunRotationNotificationRepository salmonRunRotationNotificationRepository;

	@Autowired
	public void setSalmonRunRotationNotificationRepository(Splatoon2SalmonRunRotationNotificationRepository salmonRunRotationNotificationRepository) {
		this.salmonRunRotationNotificationRepository = salmonRunRotationNotificationRepository;
	}

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!sr")) {
			message = String.format("!salmon %s", message.substring("!sr".length()).trim());
		} else if (!message.startsWith("!salmon")) {
			return;
		}

		Account account = loadAccount(Long.parseLong(args.getUserId()));

		if (message.startsWith("!salmon notify")) {
			String notificationParameters = message.substring("notify".length()).trim();

			// stages
			List<SalmonRunStage> excludedStages = new ArrayList<>();
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getSalmonRunStageExcludeFilters(), excludedStages);

			List<SalmonRunStage> stages = new ArrayList<>();
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getSalmonRunStageIncludeFilters(), stages);

			if (stages.size() == 0) {
				if (excludedStages.size() == 0) {
					stages.addAll(SalmonRunStage.All);
				} else if (excludedStages.size() < SalmonRunStage.All.size()) {
					stages = SalmonRunStage.All.stream().filter(s -> !excludedStages.contains(s)).collect(Collectors.toList());
				} else {
					sender.send("**ERROR**! You can't exclude every stage!");
					return;
				}
			}

			// weapon
			List<SalmonRunWeapon> excludedWeapons = new ArrayList<>();
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getSalmonRunWeaponExcludeFilters().entrySet().stream()
							.sorted(Comparator.comparingLong(a -> a.getKey().getFlag()))
							.collect(Collectors.toList()),
					excludedWeapons);

			List<SalmonRunWeapon> includedWeapons = new ArrayList<>();
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getSalmonRunWeaponIncludeFilters().entrySet().stream()
							.sorted(Comparator.comparingLong(a -> a.getKey().getFlag()))
							.collect(Collectors.toList()),
					includedWeapons);

			includedWeapons.removeAll(excludedWeapons);

			// days
			List<DayFilter> dayFilters = new ArrayList<>();
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getDayWithoutTimeFilters(), dayFilters);

			if (dayFilters.isEmpty()) {
				dayFilters.addAll(DayFilter.All);
			}

			if (dayFilters.size() != 7 && (account.getTimezone() == null || account.getTimezone().isBlank())) {
				sender.send("**ERROR**! To filter by days, please use **!timezone** to set your timezone first!");
				return;
			}

			// random
			List<SalmonRunRandomFilter> randomFilters = new ArrayList<>();
			regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getSalmonRunRandomFilters(), randomFilters);

			if (randomFilters.isEmpty()) {
				randomFilters.addAll(SalmonRunRandomFilter.All);
			}

			// save in database
			Splatoon2SalmonRunRotationNotification notification = new Splatoon2SalmonRunRotationNotification();

			notification.setAccount(account);
			notification.setStages(SalmonRunStage.resolveToNumber(stages));
			notification.setIncludedWeapons(SalmonRunWeapon.resolveToNumber(includedWeapons));
			notification.setExcludedWeapons(SalmonRunWeapon.resolveToNumber(excludedWeapons));
			notification.setDays(DayFilter.resolveToNumber(dayFilters));
			notification.setIncludedRandom(SalmonRunRandomFilter.resolveToNumber(randomFilters));

			notification = salmonRunRotationNotificationRepository.save(notification);

			StringBuilder responseBuilder = new StringBuilder("Alright! The following notification has been added:\n");
			fillNotificationIntoStringBuilder(notification, responseBuilder);
			responseBuilder.append("\n\nI'm gonna send you a private message as soon as a rotation appears which matches one of your notifications.");

			sender.send(responseBuilder.toString());
		} else if (message.startsWith("!salmon notifications")) {
			message = message.substring("!salmon notifications".length()).trim();

			if (message.isBlank()) {
				List<Splatoon2SalmonRunRotationNotification> allNotifications = salmonRunRotationNotificationRepository.findByAccountIdOrderById(account.getId());

				if (allNotifications.size() == 0) {
					sender.send("**ERROR**! You don't have any notifications yet");
					return;
				}

				StringBuilder responseBuilder = new StringBuilder("Those are your current notifications:\n");

				for (Splatoon2SalmonRunRotationNotification notification : allNotifications) {
					writeOverviewIntoBuilder(responseBuilder, notification);
				}

				responseBuilder.append("\n\nTo receive detailed information about one of them, use **!sr notifications <id>**.");

				sender.send(responseBuilder.toString());
			} else {
				try {
					message = message.split("\\s")[0];
					int id = Integer.parseInt(message);

					Splatoon2SalmonRunRotationNotification foundNotification = salmonRunRotationNotificationRepository.findByIdAndAccountIdOrderById(id, account.getId());

					if (foundNotification != null) {
						StringBuilder responseBuilder = new StringBuilder();
						fillNotificationIntoStringBuilder(foundNotification, responseBuilder);

						sender.send(responseBuilder.toString());
					} else {
						sender.send(String.format("**ERROR**! Sorry, you don't have a notification with number **%s**", message));
					}
				} catch (NumberFormatException ignored) {
					sender.send(String.format("**ERROR**! Whatever you're trying to do, **%s** is not a number!", message));
				}
			}
		} else if (message.startsWith("!salmon delete")) {
			message = message.substring("!salmon delete".length()).trim();

			if (!message.isBlank()) {
				String[] idStrings = message.split("\\s");

				List<Long> ids = new ArrayList<>();

				for (String idString : idStrings) {
					try {
						ids.add(Long.parseLong(idString));
					} catch (NumberFormatException ignored) {
					}
				}

				List<Splatoon2SalmonRunRotationNotification> foundNotifications = salmonRunRotationNotificationRepository.findByAccountIdOrderById(account.getId()).stream()
						.filter(fn -> ids.contains(fn.getId()))
						.collect(Collectors.toList());

				if (foundNotifications.size() > 0) {
					salmonRunRotationNotificationRepository.deleteAll(foundNotifications);

					StringBuilder responseBuilder = new StringBuilder("I deleted the following notifications:");

					for (Splatoon2SalmonRunRotationNotification notification : foundNotifications) {
						writeOverviewIntoBuilder(responseBuilder, notification);
					}

					sender.send(responseBuilder.toString());
				} else {
					sender.send("**ERROR**! I could not find any notification for the numbers you gave me.");
				}
			} else {
				sender.send("**ERROR**! Please provide at least one number to delete specific notifications.");
			}
		} else if (message.startsWith("!salmon clear")) {
			List<Splatoon2SalmonRunRotationNotification> foundNotifications = salmonRunRotationNotificationRepository.findByAccountIdOrderById(account.getId());
			salmonRunRotationNotificationRepository.deleteAll(foundNotifications);

			sender.send("I deleted all your notifications as requested.");
		} else {
			sender.send("Allowed commands:\n    - !sr notify\n    - !sr notifications\n    - !sr notifications <id>\n    - !sr clear\n    - !sr delete <id>" +
					"\n    - !salmon notify\n    - !salmon notifications\n    - !salmon notifications <id>\n    - !salmon clear\n    - !salmon delete <id>");
		}
	}

	private void writeOverviewIntoBuilder(StringBuilder responseBuilder, Splatoon2SalmonRunRotationNotification notification) {
		responseBuilder.append("\n").append("- Id: **").append(notification.getId())
				.append("** - Days: **");

		getDayString(responseBuilder, List.of(DayFilter.resolveFromNumber(notification.getDays())));
	}

	private Account loadAccount(long discordId) {
		Account account = accountRepository.findByDiscordIdOrderById(discordId).stream().findFirst().orElse(null);

		if (account == null) {
			account = new Account(0L, discordId, null, null, null, false, null, null, null);
		}

		return account;
	}

	private void fillNotificationIntoStringBuilder(Splatoon2SalmonRunRotationNotification notification, StringBuilder builder) {
		writeOverviewIntoBuilder(builder, notification);

		boolean atLeastSecond;
		SalmonRunStage[] included = SalmonRunStage.resolveFromNumber(notification.getStages());
		if (included.length > 0) {
			builder.append("\n    - Stages: ");
			atLeastSecond = false;
			for (SalmonRunStage stage : included) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(stage.getName());
				atLeastSecond = true;
			}
		}

		SalmonRunWeapon[] includedWeapons = SalmonRunWeapon.resolveFromNumber(notification.getIncludedWeapons());
		if (includedWeapons.length > 0) {
			builder.append("\n    - At least one of these weapons: ");
			atLeastSecond = false;
			for (SalmonRunWeapon weapon : includedWeapons) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(weapon.getName());
				atLeastSecond = true;
			}
		}

		SalmonRunWeapon[] excludedWeapons = SalmonRunWeapon.resolveFromNumber(notification.getExcludedWeapons());
		if (excludedWeapons.length > 0) {
			builder.append("\n    - None of these weapons: ");
			atLeastSecond = false;
			for (SalmonRunWeapon weapon : excludedWeapons) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(weapon.getName());
				atLeastSecond = true;
			}
		}

		SalmonRunRandomFilter[] randomChoices = SalmonRunRandomFilter.resolveFromNumber(notification.getIncludedRandom());
		if (randomChoices.length > 0) {
			builder.append("\n    - Random weapons: ");
			atLeastSecond = false;
			for (SalmonRunRandomFilter randomChoice : randomChoices) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(randomChoice.getName());
				atLeastSecond = true;
			}
		}
	}

	private void getDayString(StringBuilder builder, List<DayFilter> days) {
		boolean atLeastSecond = false;
		if (days.contains(DayFilter.Monday)) {
			builder.append("Mo");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Tuesday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("Tu");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Wednesday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("We");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Thursday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("Th");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Friday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("Fr");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Saturday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("Sa");
			atLeastSecond = true;
		}
		if (days.contains(DayFilter.Sunday)) {
			if (atLeastSecond) {
				builder.append("**, **");
			}
			builder.append("Su");
		}

		builder.append("**");
	}
}

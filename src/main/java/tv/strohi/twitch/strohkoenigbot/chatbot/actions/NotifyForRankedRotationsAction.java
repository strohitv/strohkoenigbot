package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.DayFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SplatoonStage;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TextFilters;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2RotationNotification;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.Splatoon2RotationNotificationRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class NotifyForRankedRotationsAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private TextFilters textFilters;

	@Autowired
	public void setTextFilters(TextFilters textFilters) {
		this.textFilters = textFilters;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2RotationNotificationRepository rotationNotificationRepository;

	@Autowired
	public void setRotationNotificationRepository(Splatoon2RotationNotificationRepository rotationNotificationRepository) {
		this.rotationNotificationRepository = rotationNotificationRepository;
	}

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (!message.startsWith("!ranked")) {
			return;
		}

		message = message.substring("!ranked".length()).trim();

		Account account = loadAccount(Long.parseLong(args.getUserId()));

		if (message.startsWith("notify")) {
			addNotification(sender, message, account);
		} else if (message.startsWith("notifications")) {
			message = message.substring("notifications".length()).trim();

			if (message.isBlank()) {
				List<Splatoon2RotationNotification> allNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(ModeFilter.Ranked, account.getId());

				if (allNotifications.size() == 0) {
					sender.send("**ERROR**! You don't have any notifications yet");
					return;
				}

				StringBuilder responseBuilder = new StringBuilder("Those are your current notifications:");

				for (Splatoon2RotationNotification notification : allNotifications) {
					responseBuilder.append("\n    ").append("- Id: **").append(notification.getId())
							.append("** - Mode: **").append(notification.getMode().getName())
							.append("** - Rule: **").append(notification.getRule().getName())
							.append("**");
				}

				responseBuilder.append("\n\nTo receive detailed information about one of them, use **!ranked notifications <id>**.");

				sender.send(responseBuilder.toString());
			} else {
				try {
					message = message.split("\\s")[0];
					int id = Integer.parseInt(message);

					Splatoon2RotationNotification foundnotification = rotationNotificationRepository.findByIdAndAccountIdOrderById(id, account.getId());

					if (foundnotification != null) {
						StringBuilder responseBuilder = new StringBuilder();
						fillNotificationIntoStringBuilder(foundnotification, responseBuilder);

						sender.send(responseBuilder.toString());
					} else {
						sender.send(String.format("**ERROR**! Sorry, you don't have a notification with number **%s**", message));
					}
				} catch (NumberFormatException ignored) {
					sender.send(String.format("**ERROR**! Whatever you're trying to do, **%s** is not a number!", message));
				}
			}

		} else if (message.startsWith("unnotify")) {
			message = message.substring("unnotify".length()).trim();

			if (!message.isBlank()) {
				String[] idStrings = message.split("\\s");

				List<Long> ids = new ArrayList<>();

				for (String idString : idStrings) {
					try {
						ids.add(Long.parseLong(idString));
					} catch (NumberFormatException ignored) {
					}
				}

				List<Splatoon2RotationNotification> foundNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(ModeFilter.Ranked, account.getId()).stream()
						.filter(fn -> ids.contains(fn.getId()))
						.collect(Collectors.toList());

				if (foundNotifications.size() > 0) {
					rotationNotificationRepository.deleteAll(foundNotifications);

					StringBuilder responseBuilder = new StringBuilder("I deleted the following notifications:");

					for (Splatoon2RotationNotification notification : foundNotifications) {
						responseBuilder.append("\n    ").append("- Id: **").append(notification.getId())
								.append("** - Mode: **").append(notification.getMode().getName())
								.append("** - Rule: **").append(notification.getRule().getName())
								.append("**");
					}

					sender.send(responseBuilder.toString());
				} else {
					sender.send("**ERROR**! I could not find any notification for the numbers you gave me.");
				}
			} else {
				List<Splatoon2RotationNotification> foundNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(ModeFilter.Ranked, account.getId());
				rotationNotificationRepository.deleteAll(foundNotifications);

				sender.send("I deleted all your notifications as requested.");
			}
		}
	}

	private void addNotification(TwitchDiscordMessageSender sender, String message, Account account) {
		if (rotationNotificationRepository.findByModeAndAccountIdOrderById(ModeFilter.Ranked, account.getId()).size() >= 12) {
			sender.send("**ERROR**! You already have 12 notifications for ranked! Remove some old ones first before adding a new one!");
			return;
		}

		String notificationParameters = message.substring("notify".length()).trim();

		// stages
		List<SplatoonStage> excludedStages = new ArrayList<>();
		notificationParameters = fillListAndReplaceText(notificationParameters, textFilters.getStageExcludeFilters(), excludedStages);

		List<SplatoonStage> includedStages = new ArrayList<>();
		notificationParameters = fillListAndReplaceText(notificationParameters, textFilters.getStageIncludeFilters(), includedStages);
		includedStages.removeAll(excludedStages);

		// rules
		List<RuleFilter> includedRules = new ArrayList<>();
		notificationParameters = fillListAndReplaceText(notificationParameters, textFilters.getRuleFilters(), includedRules);

		if (includedRules.size() == 0) {
			includedRules.addAll(RuleFilter.RankedModes);
		}

		// days
		List<DayFilterWithTimeString> dayFiltersWithTime = new ArrayList<>();
		notificationParameters = fillDayFilterWithTimeList(notificationParameters, textFilters.getDayWithTimeFilters(), dayFiltersWithTime);

		List<DayFilter> dayFilters = new ArrayList<>();
		notificationParameters = fillListAndReplaceText(notificationParameters, textFilters.getDayWithoutTimeFilters(), dayFilters);

		if (dayFiltersWithTime.isEmpty() && dayFilters.isEmpty()) {
			dayFilters.addAll(DayFilter.All);
		}

		// global time
		String globalTimeFilter = textFilters.getTimeFilter()
				.matcher(notificationParameters)
				.results()
				.map(mr -> mr.group(0))
				.findFirst()
				.orElse("0-23")
				.replaceAll("\\s", "");

		// validation
		int globalStartTime = Integer.parseInt(globalTimeFilter.split("-")[0]);
		int globalEndTime = Integer.parseInt(globalTimeFilter.split("-")[1]);
		if (globalEndTime < globalStartTime) {
			globalEndTime = 23;
		}

		for (DayFilterWithTimeString combined : dayFiltersWithTime) {
			if (combined.getEnd() < combined.getStart()) {
				combined.setEnd(23);
			}
		}

		if ((globalStartTime != 0 || globalEndTime != 23 || dayFilters.size() != 7 || dayFiltersWithTime.size() > 0)
				&& (account.getTimezone() == null || account.getTimezone().isBlank())) {
			sender.send("**ERROR**! To filter by time, please use **!timezone** to set your timezone first!");
			return;
		}

		List<Splatoon2RotationNotification> notificationsToAdd = new ArrayList<>();
		for (RuleFilter rule : includedRules) {
			Splatoon2RotationNotification notification = new Splatoon2RotationNotification();
			notification.setAccount(account);

			notification.setMode(ModeFilter.Ranked);
			notification.setRule(rule);

			notification.setIncludedStages(SplatoonStage.resolveToNumber(includedStages));
			notification.setExcludedStages(SplatoonStage.resolveToNumber(excludedStages));

			notification.setNotifyMonday(dayFilters.contains(DayFilter.Monday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Monday));
			notification.setStartTimeMonday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Monday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeMonday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Monday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifyTuesday(dayFilters.contains(DayFilter.Tuesday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Tuesday));
			notification.setStartTimeTuesday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Tuesday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeTuesday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Tuesday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifyWednesday(dayFilters.contains(DayFilter.Wednesday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Wednesday));
			notification.setStartTimeWednesday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Wednesday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeWednesday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Wednesday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifyThursday(dayFilters.contains(DayFilter.Thursday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Thursday));
			notification.setStartTimeThursday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Thursday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeThursday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Thursday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifyFriday(dayFilters.contains(DayFilter.Friday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Friday));
			notification.setStartTimeFriday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Friday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeFriday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Friday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifySaturday(dayFilters.contains(DayFilter.Saturday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Saturday));
			notification.setStartTimeSaturday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Saturday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeSaturday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Saturday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notification.setNotifySunday(dayFilters.contains(DayFilter.Sunday) || dayFiltersWithTime.stream().anyMatch(dfwt -> dfwt.getFilter() == DayFilter.Sunday));
			notification.setStartTimeSunday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Sunday)
					.map(DayFilterWithTimeString::getStart)
					.findFirst()
					.orElse(globalStartTime));
			notification.setEndTimeSunday(dayFiltersWithTime.stream()
					.filter(dfwt -> dfwt.getFilter() == DayFilter.Sunday)
					.map(DayFilterWithTimeString::getEnd)
					.findFirst()
					.orElse(globalEndTime));

			notificationsToAdd.add(notification);
		}

		Iterable<Splatoon2RotationNotification> addedNotifications = rotationNotificationRepository.saveAll(notificationsToAdd);

		StringBuilder responseBuilder = new StringBuilder("Alright! The following notifications have been added:\n");

		for (Splatoon2RotationNotification notification : addedNotifications) {
			fillNotificationIntoStringBuilder(notification, responseBuilder);
		}

		responseBuilder.append("\n\nI'm gonna send you a private message as soon as a rotation appears which matches your filters.");
		sender.send(responseBuilder.toString());
	}

	private Account loadAccount(long discordId) {
		Account account = accountRepository.findByDiscordIdOrderById(discordId).stream().findFirst().orElse(null);

		if (account == null) {
			account = new Account(0L, discordId, null, null, null, false, null, null, null);
		}

		return account;
	}

	private <T> String fillListAndReplaceText(String text, Map<T, Pattern> map, List<T> listToFill) {
		for (Map.Entry<T, Pattern> enumAndPattern : map.entrySet()) {
			String[] results = enumAndPattern.getValue()
					.matcher(text)
					.results()
					.map(mr -> mr.group(0))
					.toArray(String[]::new);

			if (results.length > 0) {
				if (!listToFill.contains(enumAndPattern.getKey())) {
					listToFill.add(enumAndPattern.getKey());
				}

				for (String result : results) {
					text = text.replace(result, "xxx");
				}
			}
		}

		return text;
	}

	private String fillDayFilterWithTimeList(String text, Map<DayFilter, Pattern> map, List<DayFilterWithTimeString> listToFill) {
		for (Map.Entry<DayFilter, Pattern> dayAndPattern : map.entrySet()) {
			String[] results = dayAndPattern.getValue()
					.matcher(text)
					.results()
					.map(mr -> mr.group(0))
					.toArray(String[]::new);

			for (String result : results) {
				DayFilter filter = dayAndPattern.getKey();
				String time = textFilters.getTimeFilter()
						.matcher(result)
						.results()
						.map(mr -> mr.group(0))
						.findFirst()
						.orElse("0-23")
						.replaceAll("\\s", "");

				if (listToFill.stream().noneMatch(dayAndTime -> dayAndTime.filter == filter)) {
					listToFill.add(new DayFilterWithTimeString(filter, time, Integer.parseInt(time.split("-")[0]), Integer.parseInt(time.split("-")[1])));
				}

				text = text.replace(result, "xxx");
			}
		}

		return text;
	}

	private void fillNotificationIntoStringBuilder(Splatoon2RotationNotification notification, StringBuilder builder) {
		builder.append("\n").append("- Id: **").append(notification.getId())
				.append("** - Mode: **").append(notification.getMode().getName())
				.append("** - Rule: **").append(notification.getRule().getName())
				.append("**\n    - Rotation starts between: ");

		boolean atLeastSecond = false;
		if (notification.isNotifyMonday()) {
			builder.append("Mo: ").append(notification.getStartTimeMonday()).append("-").append(notification.getEndTimeMonday());
			atLeastSecond = true;
		}
		if (notification.isNotifyTuesday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("Tu: ").append(notification.getStartTimeTuesday()).append("-").append(notification.getEndTimeTuesday());
			atLeastSecond = true;
		}
		if (notification.isNotifyWednesday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("We: ").append(notification.getStartTimeWednesday()).append("-").append(notification.getEndTimeWednesday());
			atLeastSecond = true;
		}
		if (notification.isNotifyThursday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("Th: ").append(notification.getStartTimeThursday()).append("-").append(notification.getEndTimeThursday());
			atLeastSecond = true;
		}
		if (notification.isNotifyFriday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("Fr: ").append(notification.getStartTimeFriday()).append("-").append(notification.getEndTimeFriday());
			atLeastSecond = true;
		}
		if (notification.isNotifySaturday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("Sa: ").append(notification.getStartTimeSaturday()).append("-").append(notification.getEndTimeSaturday());
			atLeastSecond = true;
		}
		if (notification.isNotifySunday()) {
			if (atLeastSecond) {
				builder.append(", ");
			}
			builder.append("Su: ").append(notification.getStartTimeSunday()).append("-").append(notification.getEndTimeSunday());
		}

		SplatoonStage[] included = SplatoonStage.resolveFromNumber(notification.getIncludedStages());
		if (included.length > 0) {
			builder.append("\n    - At least one of these stages: ");
			atLeastSecond = false;
			for (SplatoonStage stage : included) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(stage.getName());
				atLeastSecond = true;
			}
		}

		SplatoonStage[] excluded = SplatoonStage.resolveFromNumber(notification.getExcludedStages());
		if (excluded.length > 0) {
			builder.append("\n    - None of these stages: ");
			atLeastSecond = false;
			for (SplatoonStage stage : excluded) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(stage.getName());
				atLeastSecond = true;
			}
		}
	}

	@Getter
	@Setter
	@AllArgsConstructor
	private static class DayFilterWithTimeString {
		private DayFilter filter;
		private String time;

		private int start;
		private int end;
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.RegexUtils;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TextFilters;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationNotification;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3RotationNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3NotifyForVsRotationsAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private final TextFilters textFilters;
	private final RegexUtils regexUtils;
	private final DiscordAccountLoader discordAccountLoader;
	private final Splatoon3RotationNotificationRepository rotationNotificationRepository;

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		ModeFilter mode;

		if (message.startsWith("!regular ")) {
			message = message.substring("!regular ".length()).trim();
			mode = ModeFilter.RegularBattle;
		} else if (message.startsWith("!series ")) {
			message = message.substring("!series ".length()).trim();
			mode = ModeFilter.AnarchySeries;
		} else if (message.startsWith("!open ")) {
			message = message.substring("!open ".length()).trim();
			mode = ModeFilter.AnarchyOpen;
		} else if (message.startsWith("!x ")) {
			message = message.substring("!x ".length()).trim();
			mode = ModeFilter.XBattle;
		} else if (message.startsWith("!openfest ")) {
			message = message.substring("!openfest ".length()).trim();
			mode = ModeFilter.SplatfestOpen;
		} else if (message.startsWith("!profest ")) {
			message = message.substring("!profest ".length()).trim();
			mode = ModeFilter.SplatfestPro;
		} else if (message.startsWith("!tricolor ")) {
			message = message.substring("!tricolor ".length()).trim();
			mode = ModeFilter.SplatfestTricolor;
		} else if (message.startsWith("!challenge ")) {
			message = message.substring("!challenge ".length()).trim();
			mode = ModeFilter.Challenge;
		} else {
			return;
		}

		Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

		if (message.startsWith("notify")) {
			addNotification(mode, sender, message, account);
		} else if (message.startsWith("notifications")) {
			message = message.substring("notifications".length()).trim();

			if (message.isBlank()) {
				List<Splatoon3VsRotationNotification> allNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(mode, account.getId());

				if (allNotifications.isEmpty()) {
					sender.send("**ERROR**! You don't have any notifications yet");
					return;
				}

				StringBuilder responseBuilder = new StringBuilder("## Current notifications");

				for (var notification : allNotifications) {
					responseBuilder.append("\n- ").append("Id: **").append(notification.getId())
						.append("** - Mode: **").append(notification.getMode().getName())
						.append("** - Rule: **").append(notification.getRule().getName())
						.append("**");
				}

				var command = getCommandName(mode);
				responseBuilder.append("\n\nTo receive detailed information about one of them, use **!").append(command).append(" notifications <id>**.");

				sender.send(responseBuilder.toString());
			} else {
				try {
					message = message.split("\\s")[0];
					int id = Integer.parseInt(message);

					var foundNotification = rotationNotificationRepository.findByIdAndAccountIdOrderById(id, account.getId());

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

		} else if (message.startsWith("clear")) {
			List<Splatoon3VsRotationNotification> foundNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(mode, account.getId());
			rotationNotificationRepository.deleteAll(foundNotifications);

			sender.send("I deleted all your notifications as requested.");
		} else if (message.startsWith("delete")) {
			message = message.substring("delete".length()).trim();

			if (!message.isBlank()) {
				String[] idStrings = message.split("\\s");

				List<Long> ids = new ArrayList<>();

				for (String idString : idStrings) {
					try {
						ids.add(Long.parseLong(idString));
					} catch (NumberFormatException ignored) {
					}
				}

				var foundNotifications = rotationNotificationRepository.findByModeAndAccountIdOrderById(mode, account.getId()).stream()
					.filter(fn -> ids.contains(fn.getId()))
					.collect(Collectors.toList());

				if (!foundNotifications.isEmpty()) {
					rotationNotificationRepository.deleteAll(foundNotifications);

					StringBuilder responseBuilder = new StringBuilder("I deleted the following notifications:");

					for (Splatoon3VsRotationNotification notification : foundNotifications) {
						responseBuilder.append("\n##  ").append("Id: __").append(notification.getId())
							.append("__ - Mode: __").append(notification.getMode().getName())
							.append("__ - Rule: __").append(notification.getRule().getName())
							.append("__");
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
			var command = getCommandName(mode);
			sender.send(String.format("## Allowed commands\n  - !%s notify\n  - !%s notifications\n  - !%s notifications <id>\n  - !%s clear\n  - !%s delete <id>", command, command, command, command, command));
		}
	}

	private String getCommandName(ModeFilter mode) {
		var command = "series";
		if (mode == ModeFilter.AnarchyOpen) {
			command = "open";
		} else if (mode == ModeFilter.XBattle) {
			command = "x";
		} else if (mode == ModeFilter.RegularBattle) {
			command = "regular";
		} else if (mode == ModeFilter.SplatfestOpen) {
			command = "openfest";
		} else if (mode == ModeFilter.SplatfestPro) {
			command = "profest";
		} else if (mode == ModeFilter.SplatfestTricolor) {
			command = "tricolor";
		} else if (mode == ModeFilter.Challenge) {
			command = "challenge";
		}

		return command;
	}

	private void addNotification(ModeFilter mode, TwitchDiscordMessageSender sender, String message, Account account) {
		if (rotationNotificationRepository.findByModeAndAccountIdOrderById(mode, account.getId()).size() >= 12) {
			sender.send("**ERROR**! You already have 12 notifications for this game mode. Remove some old ones first before adding a new one!");
			return;
		}

		var notificationParameters = message.substring("notify".length()).trim();

		// stages
		var excludedStages = new ArrayList<Splatoon3Stage>();
		notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getS3stageExcludeFilters(), excludedStages);

		var includedStages = new ArrayList<Splatoon3Stage>();
		notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getS3stageIncludeFilters(), includedStages);
		includedStages.removeAll(excludedStages);

		// rules
		var includedRules = new ArrayList<RuleFilter>();

		if (mode == ModeFilter.RegularBattle || mode == ModeFilter.SplatfestOpen || mode == ModeFilter.SplatfestPro) {
			includedRules.add(RuleFilter.TurfWar);
		} else if (mode == ModeFilter.SplatfestTricolor) {
			includedRules.add(RuleFilter.TricolorTurfWar);
		} else if (mode == ModeFilter.Challenge) {
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getAllRuleFilters(), includedRules);

			if (includedRules.isEmpty()) {
				includedRules.addAll(RuleFilter.TwoTeamModes);
			}
		} else {
			notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getRankedRuleFilters(), includedRules);

			if (includedRules.isEmpty()) {
				includedRules.addAll(RuleFilter.RankedModes);
			}
		}

		// days
		var dayFiltersWithTime = new ArrayList<DayFilterWithTimeString>();
		notificationParameters = regexUtils.fillDayFilterWithTimeList(notificationParameters, textFilters.getDayWithTimeFilters(), dayFiltersWithTime);

		var dayFilters = new ArrayList<DayFilter>();
		notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getDayWithoutTimeFilters(), dayFilters);

		var timezones = new ArrayList<String>();
		notificationParameters = regexUtils.fillListAndReplaceText(notificationParameters, textFilters.getTimezoneFilters(), timezones);

		if (dayFiltersWithTime.isEmpty() && dayFilters.isEmpty()) {
			dayFilters.addAll(DayFilter.All);
		}

		// global time
		var globalTimeFilter = textFilters.getTimeFilter()
			.matcher(notificationParameters)
			.results()
			.map(mr -> mr.group(0))
			.findFirst()
			.orElse("0-23")
			.replaceAll("\\s", "");

		// timezone
		var chosenTimeZone = timezones.stream().findFirst().orElse("Europe/Berlin");

		// validation
		var globalStartTime = Integer.parseInt(globalTimeFilter.split("-")[0]);
		var globalEndTime = Integer.parseInt(globalTimeFilter.split("-")[1]);
		if (globalEndTime < globalStartTime) {
			globalEndTime = 23;
		}

		for (DayFilterWithTimeString combined : dayFiltersWithTime) {
			if (combined.getEnd() < combined.getStart()) {
				combined.setEnd(23);
			}
		}

		if ((globalStartTime != 0 || globalEndTime != 23 || dayFilters.size() != 7 || !dayFiltersWithTime.isEmpty())
			&& (chosenTimeZone == null || chosenTimeZone.isBlank())) {
			sender.send("**ERROR**! To filter by time, please include a UTC offset in the format of +xx:yy, for example `+01:00` or `-02:00`!");
			return;
		}

		var notificationsToAdd = new ArrayList<Splatoon3VsRotationNotification>();
		for (var rule : includedRules) {
			var notification = new Splatoon3VsRotationNotification();
			notification.setAccount(account);

			notification.setMode(mode);
			notification.setRule(rule);

			notification.setIncludedStages(Splatoon3Stage.resolveToNumber(includedStages));
			notification.setExcludedStages(Splatoon3Stage.resolveToNumber(excludedStages));

			notification.setZoneId(chosenTimeZone);

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

		var addedNotifications = rotationNotificationRepository.saveAll(notificationsToAdd);

		var responseBuilder = new StringBuilder("Alright! The following notifications have been added:\n");

		for (Splatoon3VsRotationNotification notification : addedNotifications) {
			fillNotificationIntoStringBuilder(notification, responseBuilder);
		}

		responseBuilder.append("\n\nI'm gonna send you a private message as soon as a rotation appears which matches your filters.");
		sender.send(responseBuilder.toString());
	}

	private void fillNotificationIntoStringBuilder(Splatoon3VsRotationNotification notification, StringBuilder builder) {
		builder.append("\n").append("## Id: __").append(notification.getId())
			.append("__ - Mode: __").append(notification.getMode().getName())
			.append("__ - Rule: __").append(notification.getRule().getName())
			.append("__\n  - Timezone: ").append(notification.getZoneId())
			.append("\n  - Only rotations which start on: ");

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

		var included = Splatoon3Stage.resolveFromNumber(notification.getIncludedStages());
		if (included.length > 0) {
			builder.append("\n  - At least one of these stages: ");
			atLeastSecond = false;
			for (var stage : included) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(stage.getName());
				atLeastSecond = true;
			}
		}

		var excluded = Splatoon3Stage.resolveFromNumber(notification.getExcludedStages());
		if (excluded.length > 0) {
			builder.append("\n  - None of these stages: ");
			atLeastSecond = false;
			for (var stage : excluded) {
				if (atLeastSecond) {
					builder.append(", ");
				}
				builder.append(stage.getName());
				atLeastSecond = true;
			}
		}
	}
}

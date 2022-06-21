package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.DayFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunRandomFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunStage;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.SalmonRunWeapon;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2SalmonRunRotationNotification;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.Splatoon2SalmonRunRotationNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetSalmonRunSchedules;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.toIntExact;

@Component
public class SalmonWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatNetSalmonRunSchedules schedules;

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

	private RequestSender rotationLoader;

	@Autowired
	public void setRotationLoader(RequestSender rotationLoader) {
		this.rotationLoader = rotationLoader;
	}

	private TwitchMessageSender channelMessageSender;

	@Autowired
	public void setChannelMessageSender(TwitchMessageSender channelMessageSender) {
		this.channelMessageSender = channelMessageSender;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Scheduled(cron = "15 0 * * * *")
//	@Scheduled(cron = "15 * * * * *")
	public void sendDiscordNotifications() {
		refreshRotations();

		SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail = Arrays.stream(schedules.getDetails())
						.filter(s -> Instant.now().minus(10, ChronoUnit.MINUTES).isBefore(s.getStartTimeAsInstant())
								&& Instant.now().plus(5, ChronoUnit.MINUTES).isAfter(s.getStartTimeAsInstant()))
						.findFirst()
						.orElse(null);

		SplatNetSalmonRunSchedules.SplatNetScheduleDetail newAnnouncedRotation = Arrays.stream(schedules.getDetails())
				.filter(d -> d != detail)
				.filter(d -> d.getWeapons() != null)
				.findFirst()
				.orElse(null);

		if (detail != null) {
			// new rotation started!
			logger.info("Posting new salmon run rotation into discord");
			sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getSalmonRunChannel(), formatRotation(detail), detail.getStage().getImage());

			if (newAnnouncedRotation != null) {
				logger.info("Posting new next salmon run rotation into discord");
				sendDiscordMessageToChannel(DiscordChannelDecisionMaker.getSalmonRunChannel(), formatRotation(newAnnouncedRotation), newAnnouncedRotation.getStage().getImage());
			}

			logger.info("Posting new salmon run rotation into twitch");
			channelMessageSender.send("strohkoenig",
					String.format("Salmon Run started! Map: %s - Weapons: %s - online for %s hours",
							detail.getStage().getName(),
							formatWeapons(detail),
							(int) Duration.between(detail.getStartTimeAsInstant(), detail.getEndTimeAsInstant()).toHours()));

			// private message notifications
			sendDiscordMessageToUsers(detail, formatRotation(detail), detail.getStage().getImage());

			if (newAnnouncedRotation != null) {
				sendDiscordMessageToUsers(newAnnouncedRotation, formatRotation(newAnnouncedRotation), newAnnouncedRotation.getStage().getImage());
			}
		}
	}

	private void refreshRotations() {
		if (schedules == null || Arrays.stream(schedules.getDetails()).anyMatch(s -> s.getEndTimeAsInstant().isBefore(Instant.now()))) {
			logger.info("checking for new salmon run rotations");

			Account account = accountRepository.findAll().stream()
					.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank() && da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
					.findFirst()
					.orElse(new Account());

			schedules = rotationLoader.querySplatoonApiForAccount(account, "/api/coop_schedules", SplatNetSalmonRunSchedules.class);

			logger.info("got an answer from api");
			logger.info(schedules);
		}
	}

	private void sendDiscordMessageToChannel(String channelName, String message, String stageImageUrl) {
		logger.info("Sending out discord notifications to server channel '{}'", channelName);
		discordBot.sendServerMessageWithImages(channelName,
				message,
				String.format("https://app.splatoon2.nintendo.net%s", stageImageUrl));
		logger.info("Finished sending out discord notifications to server channel '{}'", channelName);
	}

	private void sendDiscordMessageToUsers(SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail, String message, String stageImageUrl) {
		logger.info("Sending out discord notifications to users");
		List<Splatoon2SalmonRunRotationNotification> notifications = salmonRunRotationNotificationRepository.findAll();

		SalmonRunStage stage = SalmonRunStage.getFromSplatNetApiName(detail.getStage().getName());

		int randomCount = toIntExact(Arrays.stream(detail.getWeapons())
				.filter(w -> w.getCoop_special_weapon() != null)
				.count());
		if (randomCount == 2 || randomCount == 3) {
			randomCount = 1;
		}

		SalmonRunRandomFilter randomFilter = SalmonRunRandomFilter.getFromSplatNetApiRandomCount(randomCount);

		List<SalmonRunWeapon> weapons = Arrays.stream(detail.getWeapons())
				.filter(w -> w.getWeapon() != null)
				.map(w -> SalmonRunWeapon.getFromSplatNetApiName(w.getWeapon().getName()))
				.collect(Collectors.toList());

		List<Account> accountsToNotify = new ArrayList<>();

		for (Splatoon2SalmonRunRotationNotification notification : notifications) {
			if (accountsToNotify.contains(notification.getAccount())) {
				continue;
			}

			List<SalmonRunRandomFilter> randomCounts = List.of(SalmonRunRandomFilter.resolveFromNumber(notification.getIncludedRandom()));
			if (!randomCounts.contains(randomFilter)) {
				continue;
			}

			if (!List.of(SalmonRunStage.resolveFromNumber(notification.getStages())).contains(stage)) {
				continue;
			}

			SalmonRunWeapon[] excludedWeapons = SalmonRunWeapon.resolveFromNumber(notification.getExcludedWeapons());
			if (excludedWeapons.length > 0 && Stream.of(excludedWeapons).anyMatch(weapons::contains)) {
				continue;
			}

			SalmonRunWeapon[] includedWeapons = SalmonRunWeapon.resolveFromNumber(notification.getIncludedWeapons());
			if (includedWeapons.length > 0 && Stream.of(includedWeapons).noneMatch(weapons::contains)) {
				continue;
			}

			if (notification.getAccount().getTimezone() != null && !notification.getAccount().getTimezone().isBlank()) {
				// can check timezone
				DayOfWeek day = detail.getStartTimeAsInstant().atZone(ZoneId.of(notification.getAccount().getTimezone())).getDayOfWeek();

				DayFilter[] dayFilters = DayFilter.resolveFromNumber(notification.getDays());
				if (dayFilters.length > 0 && Stream.of(dayFilters).noneMatch(d -> d.getDay() == day)) {
					continue;
				}
			}

			accountsToNotify.add(notification.getAccount());
		}

		for (Account account : accountsToNotify) {
			discordBot.sendPrivateMessageWithImages(account.getDiscordId(),
					message,
					String.format("https://app.splatoon2.nintendo.net%s", stageImageUrl));
			logger.info("Finished sending out discord notifications to discord user '{}'", account.getDiscordId());
		}

		logger.info("Finished sending out discord notifications to discord users");
	}

	private String formatRotation(SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail) {
		StringBuilder builder;

		if (Instant.now().isAfter(detail.getStartTimeAsInstant())) {
			// current rotation
			builder = new StringBuilder("**Current Salmon Run rotation**\n\n**Stage**:\n- ");
		} else {
			// future rotation
			builder = new StringBuilder("**Next Salmon Run** rotation starting in **")
					.append(Duration.between(Instant.now(), detail.getStartTimeAsInstant()).abs().toHours() + 1)
					.append("** hours\nIt will start at **<t:")
					.append(detail.getStart_time())
					.append(":F>**\n\n**Stage**:\n- ");
		}

		builder.append(detail.getStage().getName())
				.append("\n\n**Weapons**:\n");

		for (SplatNetSalmonRunSchedules.SplatNetScheduleDetail.WeaponDetail weapon : detail.getWeapons()) {
			if (weapon.getWeapon() != null) {
				builder.append("- ").append(weapon.getWeapon().getName()).append("\n");
			} else if (weapon.getCoop_special_weapon() != null) {
				builder.append("- ").append(weapon.getCoop_special_weapon().getName()).append("\n");
			}
		}

		builder.append("\nRotation will be running for **")
				.append((int) Duration.between(detail.getStartTimeAsInstant(), detail.getEndTimeAsInstant()).toHours())
				.append("** hours!");

		return builder.toString();
	}

	private String formatWeapons(SplatNetSalmonRunSchedules.SplatNetScheduleDetail detail) {
		StringBuilder builder = new StringBuilder();

		for (SplatNetSalmonRunSchedules.SplatNetScheduleDetail.WeaponDetail weapon : detail.getWeapons()) {
			if (weapon.getWeapon() != null) {
				builder.append(weapon.getWeapon().getName()).append(", ");
			} else if (weapon.getCoop_special_weapon() != null) {
				builder.append(weapon.getCoop_special_weapon().getName()).append(", ");
			}
		}

		return builder.substring(0, builder.length() - 2);
	}
}

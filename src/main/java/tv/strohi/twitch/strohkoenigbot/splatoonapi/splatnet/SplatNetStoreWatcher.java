package tv.strohi.twitch.strohkoenigbot.splatoonapi.splatnet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.GearType;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchWhisperSender;
import tv.strohi.twitch.strohkoenigbot.data.model.AbilityNotification;
import tv.strohi.twitch.strohkoenigbot.data.repository.AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMerchandises;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SplatNetStoreWatcher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private RequestSender shopLoader;

	@Autowired
	public void setShopLoader(RequestSender shopLoader) {
		this.shopLoader = shopLoader;
	}

	private TwitchMessageSender channelMessageSender;

	@Autowired
	public void setChannelMessageSender(TwitchMessageSender channelMessageSender) {
		this.channelMessageSender = channelMessageSender;
	}

	private TwitchWhisperSender privateMessageSender;

	@Autowired
	public void setPrivateMessageSender(TwitchWhisperSender privateMessageSender) {
		this.privateMessageSender = privateMessageSender;
	}

	private AbilityNotificationRepository abilityNotificationRepository;

	@Autowired
	public void setAbilityNotificationRepository(AbilityNotificationRepository abilityNotificationRepository) {
		this.abilityNotificationRepository = abilityNotificationRepository;
	}

	// todo: check user role
	// todo: read gear from splatnet api
	// todo: read notification subscriptions from database (table still todo)
	// todo: rate limiting for pns (extra class)
	// todo: https://dev.twitch.tv/docs/irc/guide

	// 10 seconds after each full hour
	@Scheduled(cron = "10 0 * * * *")
	public void refreshSplatNetShop() {
		SplatoonMerchandises gearOffers = shopLoader.querySplatoonApi("/api/onlineshop/merchandises", SplatoonMerchandises.class);
		System.out.println(gearOffers);

		if (gearOffers != null && gearOffers.getMerchandises() != null) {
			if (gearOffers.getMerchandises().length >= 1 && gearOffers.getMerchandises()[0].getEndTime().isBefore(Instant.now().plus(1, ChronoUnit.HOURS))) {
				SplatoonMerchandises.SplatoonMerchandise gear = gearOffers.getMerchandises()[0];

				String message = String.format("%d MINUTES LEFT FOR THIS GEAR IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d",
						Duration.between(Instant.now(), gear.getEndTime()).abs().toMinutes() + 1,
						gear.getKind(), gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity());

				channelMessageSender.send("strohkoenig", message);

				try {
					List<AbilityNotification> notifications = abilityNotificationRepository.findAll().stream()
							.filter(an ->
									(an.getGear() == GearType.Any || an.getGear() == Arrays.stream(GearType.values()).filter(gt -> gt.getName().equals(gear.getGear().getKind())).findFirst().orElse(GearType.Any))
											|| (an.getMain() == AbilityType.Any || an.getMain() == Arrays.stream(AbilityType.values()).filter(at -> at.getName().equals(gear.getSkill().getName())).findFirst().orElse(AbilityType.Any))
											|| (an.getFavored() == AbilityType.Any || an.getFavored() == Arrays.stream(AbilityType.values()).filter(at -> at.getName().equals(gear.getGear().getBrand().getFrequent_skill().getName())).findFirst().orElse(AbilityType.Any)))
							.collect(Collectors.toList());

					for (AbilityNotification notification : notifications) {
						if (privateMessageSender.sendMessageToChannelWithId(notification.getUserId(), message)) {
							abilityNotificationRepository.delete(notification);
						}
					}
				} catch (Exception ex) {
					logger.error(ex);
				}
			}

			if (gearOffers.getMerchandises().length > 1 && gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1].getEndTime().isAfter(Instant.now().plus(11, ChronoUnit.HOURS))) {
				SplatoonMerchandises.SplatoonMerchandise gear = gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1];

				String message = String.format("NEW GEAR ARRIVED IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d. Available for %d hours.",
						gear.getKind(),
						gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity(), Duration.between(Instant.now(),
								gear.getEndTime()).abs().toHours() + 1);

				channelMessageSender.send("strohkoenig", message);

				List<AbilityNotification> notifications = abilityNotificationRepository.findAll().stream()
						.filter(an ->
								(an.getGear() == GearType.Any || an.getGear() == GearType.valueOf(gear.getGear().getKind()))
										|| (an.getMain() == AbilityType.Any || an.getMain() == AbilityType.valueOf(gear.getSkill().getName()))
										|| (an.getFavored() == AbilityType.Any || an.getFavored() == AbilityType.valueOf(gear.getGear().getBrand().getFrequent_skill().getName()))
						)
						.collect(Collectors.toList());

				for (AbilityNotification notification : notifications) {
					privateMessageSender.sendMessageToChannelWithId(notification.getUserId(), message);
				}
			}
		}
	}
}

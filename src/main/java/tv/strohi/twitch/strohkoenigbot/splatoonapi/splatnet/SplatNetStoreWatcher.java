package tv.strohi.twitch.strohkoenigbot.splatoonapi.splatnet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.GearType;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.AbilityNotification;
import tv.strohi.twitch.strohkoenigbot.data.repository.AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMerchandises;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private AbilityNotificationRepository abilityNotificationRepository;

	@Autowired
	public void setAbilityNotificationRepository(AbilityNotificationRepository abilityNotificationRepository) {
		this.abilityNotificationRepository = abilityNotificationRepository;
	}

	private DiscordAccountRepository discordAccountRepository;

	@Autowired
	public void setDiscordAccountRepository(DiscordAccountRepository discordAccountRepository) {
		this.discordAccountRepository = discordAccountRepository;
	}

	// 10 seconds after each full hour
	@Scheduled(cron = "10 */3 * * * *")
//	@Scheduled(cron = "10 0 * * * *")
//	@Scheduled(cron = "10 * * * * *")
	public void refreshSplatNetShop() {
		logger.info("checking for new splatnet store offers");
		SplatoonMerchandises gearOffers = shopLoader.querySplatoonApi("/api/onlineshop/merchandises", SplatoonMerchandises.class);

		logger.info("found {} offers", gearOffers != null ? gearOffers.getMerchandises().length : 0);
		logger.info(gearOffers);


		logger.info("filters in database: ");
		logger.info(abilityNotificationRepository.findAll());

		if (gearOffers != null && gearOffers.getMerchandises() != null) {
			if (gearOffers.getMerchandises().length >= 1 && gearOffers.getMerchandises()[0].getEndTime().isBefore(Instant.now().plus(1, ChronoUnit.HOURS))) {
				SplatoonMerchandises.SplatoonMerchandise gear = gearOffers.getMerchandises()[0];

				logger.info("Sending last hour notification for gear:");
				logger.info(gear);

				String message = String.format("%d MINUTES LEFT FOR THIS GEAR IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d",
						Duration.between(Instant.now(), gear.getEndTime()).abs().toMinutes() + 1,
						gear.getKind(), gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity() + 1);

				channelMessageSender.send("strohkoenig", message);

				String discordMessage = String.format("**ONLY %d MINUTES LEFT FOR THIS GEAR IN SPLATNET GEAR SHOP**!\n\nType: **%s**\nBrand: **%s**\nName: **%s**\nPrice: **%d** coins\n\nMain ability: **%s**\nFavored ability: **%s**\nUnlocked sub slots: **%d**",
						Duration.between(Instant.now(), gear.getEndTime()).abs().toMinutes() + 1,
						gear.getKind(), gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity() + 1);

				sendDiscordNotification(gear, discordMessage);
			}

			if (gearOffers.getMerchandises().length > 1 && gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1].getEndTime().isAfter(Instant.now().plus(11, ChronoUnit.HOURS))) {
				SplatoonMerchandises.SplatoonMerchandise gear = gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1];

				logger.info("Sending new in store notification for gear:");
				logger.info(gear);

				String message = String.format("NEW GEAR ARRIVED IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d. Available for %d hours.",
						gear.getKind(),
						gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity() + 1, Duration.between(Instant.now(),
								gear.getEndTime()).abs().toHours() + 1);

				channelMessageSender.send("strohkoenig", message);

				String discordMessage = String.format("New gear arrived in splatnet gear shop!\n\nType: **%s**\nBrand: **%s**\nName: **%s**\nPrice: **%s** coins\n\nMain ability: **%s**\nFavored ability: **%s**\nUnlocked sub slots: **%s**\n\nAvailable for **%d hours**",
						gear.getKind(),
						gear.getGear().getBrand().getName(),
						gear.getGear().getName(),
						gear.getPrice(),
						gear.getSkill().getName(),
						gear.getGear().getBrand().getFrequent_skill().getName(),
						gear.getGear().getRarity() + 1, Duration.between(Instant.now(),
								gear.getEndTime()).abs().toHours() + 1);

				sendDiscordNotification(gear, discordMessage);
			}
		}
	}

	private void sendDiscordNotification(SplatoonMerchandises.SplatoonMerchandise gear, String discordMessage) {
		logger.info("Sending out discord notifications");
		List<AbilityNotification> notifications = findNotifications(gear);
		List<String> sentNotifications = new ArrayList<>();
		for (AbilityNotification notification : notifications) {
			if (!sentNotifications.contains(notification.getUserId())) {
				discordAccountRepository.findByTwitchUserId(notification.getUserId())
						.stream()
						.findFirst()
						.ifPresent(discordAccount -> {
									logger.info("Sending notification to discord account: {}", discordAccount.getDiscordId());
									discordBot.sendPrivateMessageWithImage(discordAccount.getDiscordId(),
											discordMessage,
											String.format("https://app.splatoon2.nintendo.net%s", gear.getGear().getImage()),
											String.format("https://app.splatoon2.nintendo.net%s", gear.getSkill().getImage()),
											String.format("https://app.splatoon2.nintendo.net%s", gear.getGear().getBrand().getFrequent_skill().getImage()));
								}
						);

				sentNotifications.add(notification.getUserId());
			}
		}
		logger.info("Finished sending out discord notifications");
	}

	private List<AbilityNotification> findNotifications(SplatoonMerchandises.SplatoonMerchandise gear) {
		return abilityNotificationRepository.findAll().stream()
				.filter(an ->
						(an.getGear() == GearType.Any || an.getGear() == Arrays.stream(GearType.values()).filter(gt -> gt.getName().equals(gear.getGear().getKind())).findFirst().orElse(GearType.Any))
								|| (an.getMain() == AbilityType.Any || an.getMain() == Arrays.stream(AbilityType.values()).filter(at -> at.getName().equals(gear.getSkill().getName())).findFirst().orElse(AbilityType.Any))
								|| (an.getFavored() == AbilityType.Any || an.getFavored() == Arrays.stream(AbilityType.values()).filter(at -> at.getName().equals(gear.getGear().getBrand().getFrequent_skill().getName())).findFirst().orElse(AbilityType.Any)))
				.collect(Collectors.toList());
	}
}

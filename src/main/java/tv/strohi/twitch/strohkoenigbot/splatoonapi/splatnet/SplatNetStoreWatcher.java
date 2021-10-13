package tv.strohi.twitch.strohkoenigbot.splatoonapi.splatnet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMerchandises;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SplatNetStoreWatcher {
	private RequestSender shopLoader;

	@Autowired
	public void setShopLoader(RequestSender shopLoader) {
		this.shopLoader = shopLoader;
	}
	
	private TwitchMessageSender sender;

	@Autowired
	public void setSender(TwitchMessageSender sender) {
		this.sender = sender;
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
				sender.send("strohkoenig", String.format("%d MINUTES LEFT FOR THIS GEAR IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d", Duration.between(Instant.now(), gear.getEndTime()).abs().toMinutes() + 1, gear.getKind(), gear.getGear().getBrand().getName(), gear.getGear().getName(), gear.getPrice(), gear.getSkill().getName(), gear.getGear().getBrand().getFrequent_skill().getName(), gear.getGear().getRarity()));
			}

			if (gearOffers.getMerchandises().length > 1 && gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1].getEndTime().isAfter(Instant.now().plus(11, ChronoUnit.HOURS))) {
				SplatoonMerchandises.SplatoonMerchandise gear = gearOffers.getMerchandises()[gearOffers.getMerchandises().length - 1];
				sender.send("strohkoenig", String.format("NEW GEAR ARRIVED IN SPLATNET GEAR SHOP: %s - brand: %s - \"%s\" - price: %d coins - main ability: %s - favored ability: %s - unlocked sub slots: %d. Available for %d hours.", gear.getKind(), gear.getGear().getBrand().getName(), gear.getGear().getName(), gear.getPrice(), gear.getSkill().getName(), gear.getGear().getBrand().getFrequent_skill().getName(), gear.getGear().getRarity(), Duration.between(Instant.now(), gear.getEndTime()).abs().toHours() + 1));
			}
		}
	}
}

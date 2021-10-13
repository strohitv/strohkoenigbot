package tv.strohi.twitch.strohkoenigbot.splatoonapi.splatnet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMerchandises;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

@Component
public class SplatNetStoreWatcher {
	private RequestSender shopLoader;

	@Autowired
	public void setShopLoader(RequestSender shopLoader) {
		this.shopLoader = shopLoader;
	}

	// todo: check user role
	// todo: read gear from splatnet api
	// todo: read notification subscriptions from database (table still todo)
	// todo: rate limiting for pns (extra class)
	// todo: https://dev.twitch.tv/docs/irc/guide

	@Scheduled(cron = "1 * * * * *")
	public void refreshSplatNetShop() {
		SplatoonMerchandises test = shopLoader.querySplatoonApi("/api/onlineshop/merchandises", SplatoonMerchandises.class);
		System.out.println(test);
	}
}

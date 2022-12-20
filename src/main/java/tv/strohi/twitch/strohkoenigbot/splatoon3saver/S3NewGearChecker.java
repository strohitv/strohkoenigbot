package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.OwnedGearAndWeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.SplatNetShopResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.GearOffer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class S3NewGearChecker {
//	private static final String ALL_GEAR_GRAPHQL_KEY = "d29cd0c2b5e6bac90dd5b817914832f8";
//	private static final String SPLATNET_SHOP_GRAPHQL_KEY = "a43dd44899a09013bcfd29b4b13314ff";

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;

	private final DiscordBot discordBot;

	private final AccountRepository accountRepository;

	private final S3ApiQuerySender requestSender;

	private final S3DailyStatsSender dailyStatsSender;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3NewGearChecker_schedule", CronSchedule.getScheduleString("15 1 * * * *"), this::checkForNewGearInSplatNetShop);
	}
//	public void registerSchedule() {
//		schedulingService.register("S3NewGearChecker_schedule", CronSchedule.getScheduleString("15 * * * * *"), this::checkForNewGearInSplatNetShop);
//	}

	public void checkForNewGearInSplatNetShop() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			try {
//				SplatNetShopResult result = new ObjectMapper().readValue(new File("C:\\Users\\marco\\Documents\\win 8.1 vorteile\\test\\splatnetshop_test.txt").toURI().toURL(), SplatNetShopResult.class);
//				System.out.println(result);

				String allGearResponse = requestSender.queryS3Api(account, S3RequestKey.OwnedWeaponsAndGear.getKey());
				OwnedGearAndWeaponsResult ownedGear = new ObjectMapper().readValue(allGearResponse, OwnedGearAndWeaponsResult.class);

//				ownedGear.getData().getHeadGears().setNodes(Arrays.stream(ownedGear.getData().getHeadGears().getNodes()).filter(g -> !"Classic Bowler".equals(g.getName())).toArray(Gear[]::new));

				String splatNetGearResponse = requestSender.queryS3Api(account, S3RequestKey.SplatNetShop.getKey());
				SplatNetShopResult splatNetOffers = new ObjectMapper().readValue(splatNetGearResponse, SplatNetShopResult.class);

				if (account.getIsMainAccount()) {
					List<Gear> allGear = new ArrayList<>();
					allGear.addAll(List.of(ownedGear.getData().getHeadGears().getNodes()));
					allGear.addAll(List.of(ownedGear.getData().getClothingGears().getNodes()));
					allGear.addAll(List.of(ownedGear.getData().getShoesGears().getNodes()));
					dailyStatsSender.setGear(allGear);
				}

				for (GearOffer offer : splatNetOffers.getAllOffers()) {
					Gear[] gearToSearch;

					if ("HeadGear".equals(offer.getGear().get__typename())) {
						gearToSearch = ownedGear.getData().getHeadGears().getNodes();
					} else if ("ClothingGear".equals(offer.getGear().get__typename())) {
						gearToSearch = ownedGear.getData().getClothingGears().getNodes();
					} else if ("ShoesGear".equals(offer.getGear().get__typename())) {
						gearToSearch = ownedGear.getData().getShoesGears().getNodes();
					} else {
						throw new RuntimeException(String.format("Unkown gear type '%s'", offer.getGear().get__typename()));
					}

					boolean isNew = Arrays.stream(gearToSearch).noneMatch(gear -> gear.getName().trim().equals(trim(offer.getGear().getName())));

					long hours = Duration.between(Instant.now(), offer.getSaleEndTimeAsInstant()).abs().toHours() + 1;

					if (isNew && (hours == 1 || hours == 24)) {
						String discordMessage = String.format("__**There's gear in splatnet gear shop which you don't own yet!**__\n\n" +
										"Type: **%s**\n" +
										"Brand: **%s**\n" +
										"Name: **%s**\n" +
										"Price: **%s** coins\n\n" +
										"Main ability: **%s**\n" +
										"Favored ability: **%s**\n\n" +
										"Number of stars: **%d**\n" +
										"(= **%d** unlocked sub slots)\n\n" +
										"Available for **%d hours**",
								offer.getGear().get__typename(),
								offer.getGear().getBrand().getName(),
								offer.getGear().getName(),
								offer.getPrice(),
								offer.getGear().getPrimaryGearPower().getName(),
								offer.getGear().getBrand().getUsualGearPower() != null
										? offer.getGear().getBrand().getUsualGearPower().getName()
										: getUsualGearPowers().getOrDefault(offer.getGear().getBrand().getName(), "None"),
								offer.getGear().getAdditionalGearPowers().size() - 1,
								offer.getGear().getAdditionalGearPowers().size(),
								hours);

						List<String> images = new ArrayList<>(Arrays.asList(
								offer.getGear().getImage().getUrl(),
								offer.getGear().getPrimaryGearPower().getImage().getUrl()
						));

						if (offer.getGear().getBrand().getUsualGearPower() != null) {
							images.add(offer.getGear().getBrand().getUsualGearPower().getImage().getUrl());
						}

						logger.info("Sending notification to discord account: {}", account.getDiscordId());
						discordBot.sendPrivateMessageWithImages(account.getDiscordId(),
								discordMessage,
								images.toArray(String[]::new));
						logger.info("Done sending notification to discord account: {}", account.getDiscordId());
					}
				}

				System.out.println(allGearResponse);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private String trim(String stringToTrim) {
		if (stringToTrim == null) {
			return null;
		}

		return stringToTrim.trim();
	}

	private Map<String, String> getUsualGearPowers() {
		var map = new HashMap<String, String>();

		map.put("Splash Mob", "Ink Saver (Main)");
		map.put("Toni Kensa", "Ink Saver (Main)");
		map.put("Firefin", "Ink Saver (Sub)");
		map.put("Annaki", "Ink Saver (Sub)");
		map.put("Tentatek", "Ink Recovery Up");
		map.put("Rockenberg", "Run Speed Up");
		map.put("Krak-On", "Swim Speed Up");
		map.put("Takoroka", "Special Charge Up");
		map.put("Zekko", "Special Saver");
		map.put("Forge", "Special Power Up");
		map.put("Skalop", "Quick Respawn");
		map.put("Zink", "Quick Super Jump");
		map.put("Enperry", "Sub Power Up");
		map.put("SquidForce", "Ink Resistance Up");
		map.put("Inkline", "Sub Resistance Up");
		map.put("Barazushi", "Intensify Action");
		map.put("Emberz", "Intensify Action");

		return map;
	}
}

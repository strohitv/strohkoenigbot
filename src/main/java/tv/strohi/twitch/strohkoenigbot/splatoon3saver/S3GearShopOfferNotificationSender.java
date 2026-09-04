package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.ShopOffers;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOffer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearShopOfferNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsGearShopOfferRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3GearShopOfferNotificationSender implements ScheduledService {
	public static final String SHOP_OFFERS_LAST_NOTIFIED_DAY_CONFIG_NAME = "S3GearShopOfferNotificationSender_ShopOffersLastNotifiedDay";
	public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d LLLL yyyy");

	private final DiscordBot discordBot;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsGearRepository gearRepository;
	private final Splatoon3VsGearShopOfferRepository shopOfferRepository;
	private final Splatoon3VsGearShopOfferNotificationRepository shopOfferNotificationRepository;

	@Transactional
	public boolean addShopOffers(List<ShopOffers> offers) {
		var addedCount = 0;
		try {
			for (var offer : offers) {
				var localDate = LocalDate.parse(offer.getDay(), formatter);
				var day = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();

				shopOfferRepository.deleteAllByAddedAt(day);
				var addedIterable = shopOfferRepository.saveAll(offer.getOffers().stream()
					.map(o -> Splatoon3VsGearShopOffer.builder()
						.addedAt(day)
						.gear(gearRepository.findByName(o).orElseThrow())
						.build())
					.collect(Collectors.toList()));

				var addedList = new ArrayList<>();
				addedIterable.forEach(addedList::add);
				addedCount += addedList.size();
			}
		} catch (Exception e) {
			exceptionLogger.logExceptionAsAttachment(log, "Exception occurred while adding Shop Offers to database", e);
			return false;
		}

		logSender.queueLogs(log, "## Added new gear shop offers\nA total of `%d` shop offers have been added.", addedCount);
		return true;
	}

	private void sendNotifications() {
		var lastNotifiedConfig = configurationRepository.findByConfigName(SHOP_OFFERS_LAST_NOTIFIED_DAY_CONFIG_NAME)
			.orElseGet(() -> configurationRepository.save(Configuration.builder()
				.configName(SHOP_OFFERS_LAST_NOTIFIED_DAY_CONFIG_NAME)
				.configValue("1960-01-01")
				.build()));

		var lastSent = LocalDate
			.parse(lastNotifiedConfig.getConfigValue())
			.atStartOfDay(ZoneOffset.UTC)
			.toInstant();

		var today = Instant.now().truncatedTo(ChronoUnit.DAYS);

		if (!today.isAfter(lastSent)) {
			return;
		}

		var newLastSent = LocalDate.ofInstant(today, ZoneOffset.UTC);

		configurationRepository.save(lastNotifiedConfig.toBuilder()
			.configValue(String.format(
				"%04d-%02d-%02d",
				newLastSent.getYear(),
				newLastSent.getMonth().getValue(),
				newLastSent.getDayOfMonth()))
			.build());

		var shopOffers = shopOfferRepository.findByAddedAt(today);
		var allNotifications = shopOfferNotificationRepository.findAll();

		for (var notification : allNotifications) {
			var foundOffer = shopOffers.stream()
				.filter(offer -> offer.getGear().getName().equalsIgnoreCase(notification.getGearName()))
				.findFirst();

			foundOffer.ifPresent(offer -> {
				var canOpenLocalFile = offer.getGear().getOriginalImage().getFilePath() != null
					&& !offer.getGear().getOriginalImage().getFilePath().isBlank()
					&& new File(offer.getGear().getOriginalImage().getFilePath()).exists();

				try (var gearImageStream = canOpenLocalFile
					? new FileInputStream(offer.getGear().getOriginalImage().getFilePath())
					: URI.create(offer.getGear().getOriginalImage().getUrl()).toURL().openStream()) {

					var messageBuilder = new StringBuilder("## The Shops contain a new offer you're interested in\n- ")
						.append(offer.getGear().getName())
						.append("\n- available until <t:")
						.append(today.plus(1, ChronoUnit.DAYS).getEpochSecond())
						.append(":f> (<t:")
						.append(today.plus(1, ChronoUnit.DAYS).getEpochSecond())
						.append(":R>)");

					discordBot.sendPrivateMessageWithAttachment(notification.getAccount().getDiscordId(), messageBuilder.toString(), "shop_offer_gear.png", gearImageStream);
				} catch (Exception e) {
					exceptionLogger.logExceptionAsAttachment(log, "Exception occurred while sending Shop Offer Notification", e);
				}
			});
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(new ScheduleRequest(
			"S3GearShopOfferNotificationSender_sendNotifications",
			CronSchedule.getScheduleString("45 5 * * * *"),
			this::sendNotifications,
			null
		));
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

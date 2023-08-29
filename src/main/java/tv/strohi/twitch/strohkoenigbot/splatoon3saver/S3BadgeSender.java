package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.HistoryResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3BadgeSender {
	private final static String BADGES_FILE_PATH = "resources/bot/all-badges.json";

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final List<Badge> allOwnedBadges = new ArrayList<>();

	private void setAllOwnedBadges(List<Badge> allBadges) {
		allOwnedBadges.clear();
		allOwnedBadges.addAll(allBadges);
	}

	public List<Badge> getAllOwnedBadges() {
		return List.copyOf(allOwnedBadges);
	}

	private final ObjectMapper mapper = new ObjectMapper();

	private final DiscordBot discordBot;
	private final ResourcesDownloader resourcesDownloader;

	private final AccountRepository accountRepository;

	private final S3ApiQuerySender requestSender;

	private SchedulingService schedulingService;

	@Value("classpath:html/badges/badges.html")
	Resource mainBadgeHtml;

	@Value("classpath:html/badges/singlebadge.html")
	Resource imageContainerHtml;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3BadgeLoader_schedule", CronSchedule.getScheduleString("45 2 * * * *"), this::reloadBadges);
	}

	public void reloadBadges() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true).stream().filter(Account::getIsMainAccount).collect(Collectors.toList());

		for (Account account : accounts) {
			try {
				String historyResponse = requestSender.queryS3Api(account, S3RequestKey.History.getKey());
				HistoryResult history = mapper.readValue(historyResponse, HistoryResult.class);

				if (account.getIsMainAccount()) {
					var allBadges = history.getData().getPlayHistory().getAllBadges();
					setAllOwnedBadges(allBadges);
				}

				var allBadgesYesterday = loadBadgesFailsafe();

				var list = new ArrayList<>(allOwnedBadges);
				list.removeAll(allBadgesYesterday);

				if (list.size() > 0) {
					var badgeImages = new ArrayList<BufferedImage>();
					for (var badge : allOwnedBadges) {
						String imageLocationString = resourcesDownloader.ensureExistsLocally(badge.getImage().getUrl());
						String path = Paths.get(imageLocationString).toString();

						if (imageLocationString.startsWith("https://")) {
							URL url = new URL(imageLocationString);
							badgeImages.add(ImageIO.read(url));
						} else {
							var stream = new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString());
							badgeImages.add(ImageIO.read(stream));
						}
					}

					int columns = 10;
					int lines = (int) Math.ceil(badgeImages.size() / (double) columns);

					int size = 128;
					int fontHeight = size / 3;
					int spacing = 20;

					var allBadgesImage = new BufferedImage(columns * (size + spacing) - spacing, lines * (size + fontHeight + spacing) - spacing, BufferedImage.TYPE_INT_ARGB);

					// make transparent
					var graphics = allBadgesImage.createGraphics();
					graphics.setPaint(new Color(0, 0, 0, 0));
					graphics.fillRect(0, 0, allBadgesImage.getWidth(), allBadgesImage.getHeight());

					int posX = 0;
					int posY = 0;
					int index = 0;
					var font = new Font(new JLabel().getFont().getName(), Font.BOLD, fontHeight);
					for (var badgeImage : badgeImages) {
						graphics.setPaint(new Color(255, 255, 255, 255));
						graphics.fillRect(posX, posY, size, size + fontHeight);

						graphics.setPaint(new Color(0, 0, 0, 0));
						graphics.drawImage(badgeImage, posX, posY, size, size, null);

						graphics.setPaint(new Color(0, 0, 0, 255));
						drawCenteredString(graphics, String.format("%d", index + 1), new Rectangle(posX, posY + size, size, fontHeight), font);

						index++;
						posX = (size + spacing) * (index % columns);
						posY = (size + fontHeight + spacing) * (index / columns);
					}

//				var outputfile = new File("C:\\Users\\marco\\Desktop\\testimage.png");
//				ImageIO.write(allBadgesImage, "png", outputfile);

					var builder = new StringBuilder("Found new badges:");
					for (var badge : list) {
						var indexStr = String.format("#**%03d**: ", allOwnedBadges.indexOf(badge) + 1);

						if (builder.length() + badge.getDescription().length() + indexStr.length() + "\n- ".length() > 2000) {
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3BadgesChannel(), builder.toString());
							builder = new StringBuilder();
						}

						builder.append("\n- ").append(indexStr).append(badge.getDescription());
					}

					logger.info("Sending notification to discord account: {}", account.getDiscordId());
					discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3BadgesChannel(), builder.toString(), allBadgesImage);
					logger.info("Done sending notification to discord account: {}", account.getDiscordId());

					saveBadgesFailsafe(allOwnedBadges);
				}
			} catch (Exception e) {
				logSender.sendLogs(logger, "An exception occurred during S3 badge download\nSee logs for details!");
				exceptionLogger.logException(logger, e);
			}
		}
	}

	public String getBadgesAsHtml() {
		if (allOwnedBadges.size() == 0) {
			reloadBadges();
		}

		String result = "";

		try {
			String imageTemplate = new String(Files.readAllBytes(imageContainerHtml.getFile().toPath()));
			StringBuilder imageContainerHtmlBuilder = new StringBuilder();

			int index = 1;
			for (var badge : allOwnedBadges) {
				String imageLocationString = resourcesDownloader.ensureExistsLocally(badge.getImage().getUrl());
				String path = Paths.get(imageLocationString).toString();

				var stream = new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString());

				imageContainerHtmlBuilder.append(imageTemplate
						.replace("%image%", imgToBase64String(ImageIO.read(stream)))
						.replace("%number%", String.format("%d", index)));

				index++;
			}

			String htmlTemplate = new String(Files.readAllBytes(mainBadgeHtml.getFile().toPath()));
			result = htmlTemplate.replace("%img-div%", imageContainerHtmlBuilder.toString());
		} catch (IOException e) {
			logger.error("could not read image template!!");
		}

		return result;
	}

	private String imgToBase64String(final BufferedImage img) {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();

		try {
			ImageIO.write(img, "png", os);
			return Base64.getEncoder().encodeToString(os.toByteArray());
		} catch (final IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * Draw a String centered in the middle of a Rectangle.
	 *
	 * @param g    The Graphics instance.
	 * @param text The String to draw.
	 * @param rect The Rectangle to center the text in.
	 */
	private void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
		// Get the FontMetrics
		FontMetrics metrics = g.getFontMetrics(font);
		// Determine the X coordinate for the text
		int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
		// Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
		int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
		// Set the font
		g.setFont(font);
		// Draw the String
		g.drawString(text, x, y);
	}

	private void saveBadgesFailsafe(List<Badge> badges) {
		String path = Paths.get(System.getProperty("user.dir"), BADGES_FILE_PATH).toString();
		logger.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
			try {
				mapper.writeValue(file, badges);

				logger.info("badges write successful, path: '{}'", path);
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not save badges because of an Exception!");
				logger.error("exception occured!!!");
				logger.error(e);
			}
		} else {
			logger.error("could not create directory to store the resources, returning original URL");
		}
	}

	private List<Badge> loadBadgesFailsafe() {
		String path = Paths.get(System.getProperty("user.dir"), BADGES_FILE_PATH).toString();
		logger.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (file.exists()) {
			try {
				return Arrays.stream(mapper.readValue(file, Badge[].class))
						.collect(Collectors.toCollection(ArrayList::new));
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not load badges because of an Exception!");
				logger.error("exception occured!!!");
				logger.error(e);
			}
		} else {
			logger.warn("file for badges does not exist!");
		}

		return List.of();
	}
}

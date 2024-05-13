package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Nameplate;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3NameplateRepository;
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
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3NameplateSender {
	private final static String NAMEPLATES_PATH = "resources/prod/v2/npl_img";

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final DiscordBot discordBot;
	private final ResourcesDownloader resourcesDownloader;

	private final Splatoon3NameplateRepository nameplateRepository;

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3NameplateLoader_schedule", CronSchedule.getScheduleString("0 * * * * *"), this::repostNameplates);
	}

	public void repostNameplates() {
		var unpostedNameplates = nameplateRepository.findByOwnedAndPosted(true, false).stream()
			.sorted((a, b) -> Integer.compare(
				Integer.parseInt(new String(Base64.getDecoder().decode(b.getApiId())).replaceAll("[^0-9]", "")),
				Integer.parseInt(new String(Base64.getDecoder().decode(a.getApiId())).replaceAll("[^0-9]", ""))))
			.collect(Collectors.toList());

		if (unpostedNameplates.size() > 0) {
			var allNameplates = nameplateRepository.findAllByOwned(true).stream()
				.sorted((a, b) -> Integer.compare(
					Integer.parseInt(new String(Base64.getDecoder().decode(b.getApiId())).replaceAll("[^0-9]", "")),
					Integer.parseInt(new String(Base64.getDecoder().decode(a.getApiId())).replaceAll("[^0-9]", ""))))
				.collect(Collectors.toList());

			var namePlateImages = new ArrayList<NameplateWithImage>();

			try {
				for (var nameplate : allNameplates) {
					String imageLocationString = resourcesDownloader.ensureExistsLocally(nameplate.getImage().getUrl(), NAMEPLATES_PATH);
					String path = Paths.get(imageLocationString).toString();

					if (imageLocationString.startsWith("https://")) {
						URL url = new URL(imageLocationString);
						namePlateImages.add(new NameplateWithImage(nameplate, ImageIO.read(url)));
					} else {
						var stream = new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString());
						namePlateImages.add(new NameplateWithImage(nameplate, ImageIO.read(stream)));
					}
				}

				int columns = 4;
				int lines = (int) Math.ceil(namePlateImages.size() / (double) columns);

				int sizeX = 350; //700; //
				int sizeY = 100; //200; //
				int fontWidth = sizeX;
				int fontHeight = sizeY / 3;
				int margin = 20;
				int padding = 10;

				var allNameplatesImage = new BufferedImage(columns * (sizeX + 2 * padding + margin) - margin,
					lines * (sizeY + 2 * padding + fontHeight + margin) - margin,
					BufferedImage.TYPE_INT_ARGB);

				// make transparent
				var graphics = allNameplatesImage.createGraphics();
				graphics.setPaint(new Color(0, 0, 0, 0));
				graphics.fillRect(0, 0, allNameplatesImage.getWidth(), allNameplatesImage.getHeight());

				int posX = 0;
				int posY = 0;
				int index = 0;
				var font = new Font(new JLabel().getFont().getName(), Font.BOLD, fontHeight);
				for (var nameplateWithImage : namePlateImages) {
					graphics.setPaint(new Color(255, 255, 255, 255));
					graphics.fillRect(posX, posY, sizeX + 2 * padding, sizeY + 3 * padding + fontHeight);

					graphics.setPaint(new Color(0, 0, 0, 0));
					graphics.drawImage(nameplateWithImage.getImage(), posX + padding, posY + padding, sizeX, sizeY, null);

					graphics.setPaint(new Color(0, 0, 0, 255));
					// Include Image ID: drawCenteredString(graphics, String.format("%d: %s", index + 1, new String(Base64.getDecoder().decode(nameplateWithImage.nameplate.getApiId())).replaceAll("[^0-9]", "")), new Rectangle(posX, posY + sizeY + padding, fontWidth + padding, fontHeight + 2 * padding), font);
					drawCenteredString(graphics, String.format("%d", index + 1), new Rectangle(posX, posY + sizeY + padding, fontWidth + 2 * padding, fontHeight + 2 * padding), font);

					index++;
					posX = (sizeX + 2 * padding + margin) * (index % columns);
					posY = (sizeY + 2 * padding + fontHeight + margin) * (index / columns);
				}

				var builder = new StringBuilder("Found new Banners:");
				for (var nameplate : unpostedNameplates) {
					var indexStr = String.format("#**%03d**", allNameplates.indexOf(nameplate) + 1);

					if (builder.length() + indexStr.length() + "\n- ".length() > 2000) {
						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3EmotesChannel(), builder.toString());
						builder = new StringBuilder();
					}

					builder.append("\n- ").append(indexStr);

					nameplate.setPosted(true);
				}

				nameplateRepository.saveAll(unpostedNameplates);

				logger.info("Sending nameplate notification to discord");
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3NameplatesChannel(), builder.toString(), allNameplatesImage);
				logger.info("Done sending nameplate notification to discord");
			} catch (Exception e) {
				logSender.sendLogs(logger, "An exception occurred during S3 emote download\nSee logs for details!");
				exceptionLogger.logException(logger, e);
			}
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

	@Getter
	@Setter
	@AllArgsConstructor
	private static class NameplateWithImage {
		private Splatoon3Nameplate nameplate;
		private BufferedImage image;
	}
}

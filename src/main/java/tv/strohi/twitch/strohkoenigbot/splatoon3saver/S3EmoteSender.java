package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.CatalogResult;
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
public class S3EmoteSender {
	private final static String EMOTES_FILE_PATH = "resources/bot/all-emotes.json";
	private final static String EMOTES_PATH = "resources/prod/v1/emote_img";

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final List<CatalogResult.Reward> allOwnedEmotes = new ArrayList<>();

	private void setAllOwnedEmotes(List<CatalogResult.Reward> allEmotes) {
		allOwnedEmotes.clear();
		allOwnedEmotes.addAll(allEmotes);
	}

	public List<CatalogResult.Reward> getAllOwnedEmotes() {
		return List.copyOf(allOwnedEmotes);
	}

	private final ObjectMapper mapper = new ObjectMapper();

	private final DiscordBot discordBot;
	private final ResourcesDownloader resourcesDownloader;

	private final AccountRepository accountRepository;

	private final S3ApiQuerySender requestSender;

	private SchedulingService schedulingService;

	@Value("classpath:html/emotes/emotes.html")
	Resource mainEmoteHtml;

	@Value("classpath:html/emotes/singleemote.html")
	Resource imageContainerHtml;

	@Value("classpath:already-unlocked-emotes.json")
	Resource alreadyUnlockedEmotesJSON;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3EmoteLoader_schedule", CronSchedule.getScheduleString("0 3 * * * *"), this::reloadEmotes);
	}

	public void reloadEmotes() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true).stream().filter(Account::getIsMainAccount).collect(Collectors.toList());

		for (Account account : accounts) {
			try {
				String catalogResponse = requestSender.queryS3Api(account, S3RequestKey.Catalog.getKey());
				var catalogResult = mapper.readValue(catalogResponse, CatalogResult.class);

				if (catalogResult.getData().getCatalog().getProgress() == null) {
					// new season: catalog not yet started
					catalogResult.getData().getCatalog().setProgress(new CatalogResult.ProgressStatistics());
					catalogResult.getData().getCatalog().getProgress().setRewards(new CatalogResult.Reward[0]);
				}

				if (account.getIsMainAccount()) {
					var allEmotes = new ArrayList<CatalogResult.Reward>();

					try {
						String alreadyUnlockedEmotesContent = new String(Files.readAllBytes(alreadyUnlockedEmotesJSON.getFile().toPath()));
						allEmotes.addAll(Arrays.asList(mapper.readValue(alreadyUnlockedEmotesContent, CatalogResult.Reward[].class)));
					} catch (IOException e) {
						logger.error("could not read already unlocked emotes!!");
					}

					var catalogEmotes = Arrays.stream(catalogResult.getData().getCatalog().getProgress().getRewards())
						.filter(reward -> reward.isAchieved() && reward.isEmote())
						.collect(Collectors.toList());

					catalogEmotes.forEach(emote -> emote.setSeasonName(catalogResult.getData().getCatalog().getSeasonName()));

					allEmotes.addAll(catalogEmotes);
					setAllOwnedEmotes(allEmotes);
				}

				var allOwnedEmotesSoFar = loadEmotesFailsafe();

				var list = new ArrayList<>(allOwnedEmotes);
				list.removeAll(allOwnedEmotesSoFar);

				if (list.size() > 0) {
					allOwnedEmotesSoFar.addAll(list);
					saveEmotesFailsafe(allOwnedEmotesSoFar);

					var emoteImages = new ArrayList<EmoteWithImage>();
					for (var emote : allOwnedEmotesSoFar) {
						String imageLocationString = resourcesDownloader.ensureExistsLocally(emote.getItem().getImage().getUrl(), EMOTES_PATH);
						String path = Paths.get(imageLocationString).toString();

						if (imageLocationString.startsWith("https://")) {
							URL url = new URL(imageLocationString);
							emoteImages.add(new EmoteWithImage(emote, ImageIO.read(url)));
						} else {
							var stream = new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString());
							emoteImages.add(new EmoteWithImage(emote, ImageIO.read(stream)));
						}
					}

					int columns = 3;
					int lines = (int) Math.ceil(emoteImages.size() / (double) columns);

					int sizeX = 158;
					int sizeY = 184;
					int fontWidth = 4 * sizeX;
					int fontHeight = sizeY / 4;
					int margin = 20;
					int padding = 10;

					var allEmotesImage = new BufferedImage(columns * (sizeX + 2 * padding + fontWidth + margin) - margin,
						lines * (sizeY + 2 * padding + margin) - margin,
						BufferedImage.TYPE_INT_ARGB);

					// make transparent
					var graphics = allEmotesImage.createGraphics();
					graphics.setPaint(new Color(0, 0, 0, 0));
					graphics.fillRect(0, 0, allEmotesImage.getWidth(), allEmotesImage.getHeight());

					int posX = 0;
					int posY = 0;
					int index = 0;
					var font = new Font(new JLabel().getFont().getName(), Font.BOLD, fontHeight);
					for (var emoteWithImage : emoteImages) {
						graphics.setPaint(new Color(255, 255, 255, 255));
						graphics.fillRect(posX, posY, sizeX + 2 * padding + fontWidth, sizeY + 2 * padding);

						graphics.setPaint(new Color(0, 0, 0, 0));
						graphics.drawImage(emoteWithImage.getImage(), posX + padding, posY + padding, sizeX, sizeY, null);

						graphics.setPaint(new Color(0, 0, 0, 255));
						drawCenteredString(graphics, String.format("%d: %s", index + 1, emoteWithImage.emote.getItem().getName()), new Rectangle(posX + sizeX + padding, posY, fontWidth + padding, sizeY + 2 * padding), font);

						index++;
						posX = (sizeX + 2 * padding + fontWidth + margin) * (index % columns);
						posY = (sizeY + 2 * padding + margin) * (index / columns);
					}

//				var outputfile = new File("C:\\Users\\marco\\Desktop\\testimage.png");
//				ImageIO.write(allEmotesImage, "png", outputfile);

					var builder = new StringBuilder("Found new Emotes:");
					for (var emote : list) {
						var indexStr = String.format("#**%03d**: ", allOwnedEmotesSoFar.indexOf(emote) + 1);

						if (builder.length() + emote.getItem().getName().length() + indexStr.length() + "\n- ".length() > 2000) {
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3EmotesChannel(), builder.toString());
							builder = new StringBuilder();
						}

						builder.append("\n- ").append(indexStr).append(emote.getItem().getName());

						if (emote.getSeasonName() != null) {
							builder.append(" (").append(emote.getSeasonName()).append(")");
						}
					}

					logger.info("Sending notification to discord account: {}", account.getDiscordId());
					discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getS3EmotesChannel(), builder.toString(), allEmotesImage);
					logger.info("Done sending notification to discord account: {}", account.getDiscordId());
				}
			} catch (Exception e) {
				logSender.sendLogs(logger, "An exception occurred during S3 emote download\nSee logs for details!");
				exceptionLogger.logException(logger, e);
			}
		}
	}

	public String getEmotesAsHtml() {
		var allOwnedEmotesSoFar = loadEmotesFailsafe();

		String result = "";

		try {
			String imageTemplate = new String(Files.readAllBytes(imageContainerHtml.getFile().toPath()));
			StringBuilder imageContainerHtmlBuilder = new StringBuilder();

			int index = 1;
			for (var emote : allOwnedEmotesSoFar) {
				String imageLocationString = resourcesDownloader.ensureExistsLocally(emote.getItem().getImage().getUrl());
				String path = Paths.get(imageLocationString).toString();

				var stream = new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString());

				imageContainerHtmlBuilder.append(imageTemplate
					.replace("%image%", imgToBase64String(ImageIO.read(stream)))
					.replace("%index%", String.format("%d", index))
					.replace("%name%", emote.getItem().getName()));

				index++;
			}

			String htmlTemplate = new String(Files.readAllBytes(mainEmoteHtml.getFile().toPath()));
			result = htmlTemplate.replace("%img-div%", imageContainerHtmlBuilder.toString());
		} catch (IOException e) {
			logger.error("could not read image template!!");
		} catch (Exception e) {
			logger.error("general error", e);
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

	private void saveEmotesFailsafe(List<CatalogResult.Reward> Emotes) {
		String path = Paths.get(System.getProperty("user.dir"), EMOTES_FILE_PATH).toString();
		logger.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
			try {
				mapper.writeValue(file, Emotes);

				logger.info("Emotes write successful, path: '{}'", path);
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not save Emotes because of an Exception!");
				logger.error("exception occured!!!");
				logger.error(e);
			}
		} else {
			logger.error("could not create directory to store the resources, returning original URL");
		}
	}

	private List<CatalogResult.Reward> loadEmotesFailsafe() {
		String path = Paths.get(System.getProperty("user.dir"), EMOTES_FILE_PATH).toString();
		logger.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (file.exists()) {
			try {
				return Arrays.stream(mapper.readValue(file, CatalogResult.Reward[].class))
					.collect(Collectors.toCollection(ArrayList::new));
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not load Emotes because of an Exception!");
				logger.error("exception occured!!!");
				logger.error(e);
			}
		} else {
			logger.warn("file for Emotes does not exist!");
		}

		return new ArrayList<>();
	}

	@Getter
	@Setter
	@AllArgsConstructor
	private static class EmoteWithImage {
		private CatalogResult.Reward emote;
		private BufferedImage image;
	}
}

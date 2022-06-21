package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

@Component
public class ResourcesDownloader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public String ensureExistsLocally(String splatNetResourceUrl) {
		logger.info("downloading a resource {}", splatNetResourceUrl);

		String imageUrl = splatNetResourceUrl;
		if (isValidURL(imageUrl)) {
			imageUrl = imageUrl.replace("https://app.splatoon2.nintendo.net", "");
			logger.info("new url {}", splatNetResourceUrl);
		}

		String path = Paths.get(System.getProperty("user.dir"), imageUrl).toString();
		logger.info("path {}", path);

		File file = Paths.get(path).toFile();
		if (!file.exists() && (file.getParentFile().exists() || file.getParentFile().mkdirs())) {
			logger.info("test 1");
			String downloadUrl = String.format("https://app.splatoon2.nintendo.net%s", imageUrl);

			try (
					BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
					FileOutputStream fileOutputStream = new FileOutputStream(file.getPath())
			) {
				logger.info("test 2");
				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}

				logger.info("test 3");
				String newPath = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "I downloaded an image!", newPath);

				logger.info("test 4");
				return newPath;
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not download an image because of an Exception!");
				logger.error("exception occured!!!");
				logger.error(e);
				return splatNetResourceUrl;
			}
		} else {
			logger.info("test 5");
			return path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
		}
	}

	public static boolean isValidURL(String urlString) {
		try {
			URL url = new URL(urlString);
			url.toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}

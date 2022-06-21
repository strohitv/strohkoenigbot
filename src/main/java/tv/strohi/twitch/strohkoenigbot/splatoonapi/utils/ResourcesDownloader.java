package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

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
	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public String ensureExistsLocally(String splatNetResourceUrl) {
		String imageUrl = splatNetResourceUrl;
		if (isValidURL(imageUrl)) {
			imageUrl = imageUrl.replace("https://app.splatoon2.nintendo.net", "");
		}

		String path = Paths.get(System.getProperty("user.dir"), imageUrl).toString();

		File file = Paths.get(path).toFile();
		if (!file.exists() && (file.getParentFile().exists() || file.getParentFile().mkdirs())) {
			String downloadUrl = String.format("https://app.splatoon2.nintendo.net%s", imageUrl);

			try (
					BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
					FileOutputStream fileOutputStream = new FileOutputStream(file.getPath())
			) {
				byte[] dataBuffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
					fileOutputStream.write(dataBuffer, 0, bytesRead);
				}

				String newPath = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "I downloaded an image!", newPath);
				return newPath;
			} catch (IOException e) {
				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Could not download an image because of an Exception!");
				return splatNetResourceUrl;
			}
		} else {
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

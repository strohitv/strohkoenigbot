package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.transaction.Transactional;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
@Log4j2
public class ResourcesDownloader {
	private final DiscordBot discordBot;
	private final ConfigurationRepository configurationRepository;
	private final ExceptionLogger exceptionLogger;

	@Transactional
	public String ensureExistsLocally(String splatNetResourceUrl) {
		return ensureExistsLocally(splatNetResourceUrl, null);
	}

	@Transactional
	public String ensureExistsLocally(String splatNetResourceUrl, String forcePath) {
		log.debug("downloading a resource '{}'", splatNetResourceUrl);

		String imageUrl = splatNetResourceUrl;
		if (isValidURL(imageUrl)) {
//			imageUrl = imageUrl.replace("https://app.splatoon2.nintendo.net", "");
			try {
				if (forcePath != null) {
					var url = new URL(splatNetResourceUrl);

					imageUrl = new URL(String.format("%s://%s/%s/%s", url.getProtocol(), url.getHost(), forcePath, FilenameUtils.getName(url.getPath()))).getPath();
				} else {
					imageUrl = new URL(splatNetResourceUrl).getPath();
				}
			} catch (MalformedURLException ignored) {
				// won't happen
			}
		}

		log.debug("new url '{}'", imageUrl);

		String path = Paths.get(System.getProperty("user.dir"), imageUrl).toString();
		log.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (!file.exists()) {
			if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
				String downloadUrl = splatNetResourceUrl;
				if (!isValidURL(downloadUrl)) {
					downloadUrl = String.format("https://app.splatoon2.nintendo.net%s", imageUrl);
				}

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
					discordBot.queueServerMessageWithImageUrls(DiscordChannelDecisionMaker.getDebugImageChannelName(), "I downloaded an image!", newPath);

					log.info("image download successful, path: '{}'", path);

					return newPath;
				} catch (IOException e) {
					logExceptionIfDebug("Could not download an image because of an Exception", e);
					return splatNetResourceUrl;
				}
			} else {
				log.error("could not create directory to store the resources, returning original URL");
				return splatNetResourceUrl;
			}
		} else {
			String result = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
			log.debug("resource already existed, returning '{}'", result);
			return result;
		}
	}

	private void logExceptionIfDebug(String message, Exception ex) {
		var shouldLogConfig = configurationRepository.findByConfigName("ResourcesDownloader_logDebugSwitch")
			.orElseGet(() -> configurationRepository.save(Configuration.builder()
				.configName("ResourcesDownloader_logDebugSwitch")
				.configValue("false")
				.build()));

		if ("true".equalsIgnoreCase(shouldLogConfig.getConfigValue())) {
			exceptionLogger.logExceptionAsAttachment(log, message, ex);
		} else {
			log.error(message, ex);
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

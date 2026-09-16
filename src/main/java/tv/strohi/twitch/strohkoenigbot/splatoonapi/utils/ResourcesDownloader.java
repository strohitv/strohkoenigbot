package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Attachment;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Target;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.ExceptionQueuer;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.LogQueuer;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.transaction.Transactional;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class ResourcesDownloader {
	private final LogQueuer logQueuer;
	private final ConfigurationRepository configurationRepository;
	private final ExceptionQueuer exceptionQueuer;

	@Transactional
	public String ensureExistsLocally(String splatNetResourceUrl) {
		return ensureExistsLocally(splatNetResourceUrl, null);
	}

	@Transactional
	public String ensureExistsLocally(String splatNetResourceUrl, String forcePath) {
		log.debug("downloading a resource '{}'", splatNetResourceUrl);

		var imageUrl = splatNetResourceUrl;
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

		var path = Paths.get(System.getProperty("user.dir"), imageUrl).toString();
		log.debug("path '{}'", path);

		var file = Paths.get(path).toFile();
		if (!file.exists()) {
			if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
				var downloadUrl = splatNetResourceUrl;
				if (!isValidURL(downloadUrl)) {
					downloadUrl = String.format("https://app.splatoon2.nintendo.net%s", imageUrl);
				}

				try (
					var in = new BufferedInputStream(new URL(downloadUrl).openStream());
					var fileOutputStream = new FileOutputStream(file.getPath())
				) {
					var dataBuffer = new byte[1024];
					int bytesRead;
					while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
						fileOutputStream.write(dataBuffer, 0, bytesRead);
					}

					var newPath = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
					logQueuer.infoQueue(
						log,
						List.of(Target.channel(DiscordChannelDecisionMaker.getDebugImageChannelName())),
						List.of(Attachment.fromFile(newPath)),
						"# I downloaded an image");

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
			var result = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
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
			exceptionQueuer.queueExceptionAsAttachment(log, message, ex);
		} else {
			log.error(message, ex);
		}
	}

	public static boolean isValidURL(String urlString) {
		try {
			var url = new URL(urlString);
			url.toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}

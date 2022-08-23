package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

@Component
public class ResourcesDownloader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	public String ensureExistsLocally(String splatNetResourceUrl) {
		logger.debug("downloading a resource '{}'", splatNetResourceUrl);

		String imageUrl = splatNetResourceUrl;
		if (isValidURL(imageUrl)) {
			imageUrl = imageUrl.replace("https://app.splatoon2.nintendo.net", "");
		}

		logger.debug("new url '{}'", imageUrl);

		String path = Paths.get(System.getProperty("user.dir"), imageUrl).toString();
		logger.debug("path '{}'", path);

		File file = Paths.get(path).toFile();
		if (!file.exists()) {
			if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
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

					logger.info("image download successful, path: '{}'", path);

					return newPath;
				} catch (IOException e) {
					logger.error("exception occured!!!");
					logger.error(e);
					return splatNetResourceUrl;
				}
			} else {
				logger.error("could not create directory to store the resources, returning original URL");
				return splatNetResourceUrl;
			}
		} else {
			String result = path.substring(System.getProperty("user.dir").length()).replace('\\', '/');
			logger.debug("resource already existed, returning '{}'", result);
			return result;
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

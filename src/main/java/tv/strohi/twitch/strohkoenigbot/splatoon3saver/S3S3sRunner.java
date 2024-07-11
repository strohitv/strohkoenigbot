package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3GTokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ConfigFileConnector;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3S3sRunner {
	private final S3GTokenRefresher gTokenRefresher;
	private final ConfigFileConnector configFileConnector;

	private Instant lastSuccessfulAttempt = Instant.now().minus(1, ChronoUnit.HOURS);

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;

	private final ConfigurationRepository configurationRepository;

	private final Map<String, Boolean> results = new HashMap<>();

	public Boolean getResult(String token) {
		return results.getOrDefault(token, null);
	}

	public void runS3S() {
		var token = String.format("s3s_%s", Instant.now());
		runS3S(token);
	}

	public void runS3S(String token) {
		new Thread(() -> {
			logger.info("Starting s3s refresh...");

			String scriptFormatString = configurationRepository.findAllByConfigName("s3sScript").stream().map(Configuration::getConfigValue).findFirst().orElse("python3 %s/s3s.py -o");

			List<Configuration> s3sLocations = configurationRepository.findAllByConfigName("s3sLocation");
			if (s3sLocations.size() == 0) return;

			Runtime rt = Runtime.getRuntime();
			for (Configuration singleS3SLocation : s3sLocations) {
				String configFileLocation = singleS3SLocation.getConfigValue();
				String completeCommand = String.format(scriptFormatString, configFileLocation).trim();

				logger.info(String.format("Starting download for location %s", configFileLocation));

				if (!gTokenRefresher.refreshGToken(rt, configFileLocation, completeCommand)) {
					logger.warn("Did not work..");
					if (lastSuccessfulAttempt.isBefore(Instant.now().minus(3, ChronoUnit.HOURS))) {
						logSender.sendLogs(logger, "Exception while executing s3s process!! Result wasn't 0 for at least three hours now!");
					}

					results.put(token, false);

					continue;
				}

				results.put(token, true);

				lastSuccessfulAttempt = Instant.now();

				ConfigFile configFile = configFileConnector.readConfigFile(configFileLocation);

				if (configFile == null) continue;

				String accountUUIDHash = UUID.nameUUIDFromBytes(Path.of(configFileLocation, "config.txt").toString().getBytes()).toString();
				Path directory = Path.of("game-results", accountUUIDHash);
				if (!Files.exists(directory)) {
					try {
						Files.createDirectories(directory);
					} catch (IOException e) {
						logSender.sendLogs(logger, String.format("Could not create game directory!! %s", directory));
						continue;
					}
				}

				File file = new File(".");
				List<String> directories = Arrays.stream(Objects.requireNonNull(file.list((current, name) -> new File(current, name).isDirectory()))).filter(name -> name.startsWith("export-")).collect(Collectors.toList());

				// move exported folders to back up directory
				for (String dir : directories) {
					try {
						logger.info(String.format("Moving directory %s", dir));
						Files.move(new File(dir).toPath(), directory.resolve(dir), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						logSender.sendLogs(logger, String.format("could not move directory %s", dir));
						logger.error(e);
					}
				}
			}

			logger.info("Finished s3s refresh");
		}).start();
	}
}

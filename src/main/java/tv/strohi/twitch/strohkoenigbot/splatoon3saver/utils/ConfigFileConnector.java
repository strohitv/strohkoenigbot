package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ConfigFileConnector {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	public ConfigFile readConfigFile(String configFileDirectory) {
		Path configFileLocation = Path.of(configFileDirectory, "config.txt");
		if (Files.exists(configFileLocation)) {
			try {
				return objectMapper.readValue(configFileLocation.toUri().toURL(), ConfigFile.class);
			} catch (IOException e) {
				logSender.sendLogs(logger, "Exception while loading config file, see logs!");
				logger.error(e);
			}
		} else {
			logSender.sendLogs(logger, "Config file does not exist");
		}

		return new ConfigFile();
	}

	public void storeConfigFile(String configFileDirectory, ConfigFile configFile) {
		Path configFileLocation = Path.of(configFileDirectory, "config.txt");
		try {
			objectMapper.writeValue(configFileLocation.toFile(), configFile);
		} catch (IOException e) {
			logSender.sendLogs(logger, "Could not store new config file on disk");
		}
	}
}

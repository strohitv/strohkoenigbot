package tv.strohi.twitch.strohkoenigbot.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.StrohkoenigbotApplication;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.SplatoonLogin;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.SplatoonLoginRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Component
public class JavaArgumentEvaluator {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private List<String> arguments = new ArrayList<>();

	@Autowired
	public void setArguments(@NonNull List<String> args) {
		arguments = args;
	}

	private SplatoonLoginRepository splatoonLoginRepository;

	@Autowired
	public void setSplatoonLoginRepository(SplatoonLoginRepository splatoonLoginRepository) {
		this.splatoonLoginRepository = splatoonLoginRepository;
	}

	private TwitchAuthRepository twitchAuthRepository;

	@Autowired
	public void setTwitchAuthRepository(TwitchAuthRepository twitchAuthRepository) {
		this.twitchAuthRepository = twitchAuthRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private TwitchBotClient twitchBotClient;

	@Autowired
	public void setTwitchBotClient(TwitchBotClient twitchBotClient) {
		this.twitchBotClient = twitchBotClient;
	}

	private StrohkoenigbotApplication app;

	@Autowired
	public void setApp(StrohkoenigbotApplication app) {
		this.app = app;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void evaluateArguments() {
		arguments.forEach(logger::info);

		boolean stop = arguments.stream().anyMatch(a -> a.trim().toLowerCase().startsWith("stop"));

		arguments.forEach(argument -> {
			String arg = argument.trim().toLowerCase();

			if (arg.startsWith("export")) {
				// export database entries
				ObjectMapper mapper = new ObjectMapper();
				mapper.registerModule(new JavaTimeModule());

				Map<String, Object> struct = new HashMap<>();
				struct.put("config", configurationRepository.findAll());
				struct.put("twitch", twitchAuthRepository.findAll());
				struct.put("splatoon", splatoonLoginRepository.findAll());

				try {
					String json = mapper.writeValueAsString(struct);

					BufferedWriter writer = new BufferedWriter(new FileWriter("exported.json"));
					writer.write(json);
					writer.close();
				} catch (IOException e) {
					logger.error(e);
				}
			} else if (arg.startsWith("config_url")) {
				// refresh data based on loaded config from file
				ObjectMapper mapper = new ObjectMapper();
				mapper.registerModule(new JavaTimeModule());

				try {
					Config config = mapper.readValue(new File(argument.trim().substring("config_url=".length())), Config.class);

					if (config.getConfig() != null) {
						configurationRepository.deleteAll();
						configurationRepository.saveAll(Arrays.asList(config.getConfig()));
					}

					if (config.getSplatoon() != null) {
						splatoonLoginRepository.deleteAll();
						splatoonLoginRepository.saveAll(Arrays.asList(config.getSplatoon().clone()));
					}

					if (config.getTwitch() != null) {
						twitchBotClient.stop();

						twitchAuthRepository.deleteAll();
						twitchAuthRepository.saveAll(Arrays.asList(config.getTwitch()));

						twitchBotClient.initializeClient();
					}
				} catch (IOException e) {
					logger.error(e);
				}
			}
		});

		if (app != null && stop) {
			app.shutdown();
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class Config {
		private Configuration[] config;
		private TwitchAuth[] twitch;
		private SplatoonLogin[] splatoon;
	}
}

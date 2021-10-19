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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.StrohkoenigbotApplication;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;
import tv.strohi.twitch.strohkoenigbot.data.model.SplatoonLogin;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.repository.SplatoonLoginRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.AuthLinkCreator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.SplatoonCookieHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Component
public class JavaArgumentEvaluator {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private boolean stop = false;

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

	private SplatoonCookieHandler cookieHandler;

	@Autowired
	public void setCookieHandler(SplatoonCookieHandler cookieHandler) {
		this.cookieHandler = cookieHandler;
	}

	private TwitchChatBot twitchChatBot;

	@Autowired
	public void setTwitchChatBot(TwitchChatBot twitchChatBot) {
		this.twitchChatBot = twitchChatBot;
	}

	private StrohkoenigbotApplication app;

	@Autowired
	public void setApp(StrohkoenigbotApplication app) {
		this.app = app;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void evaluateArguments() {
		arguments.forEach(logger::info);

		stop = arguments.stream().anyMatch(a -> a.trim().toLowerCase().startsWith("stop"));
		Map<String, String> extractedParams = new HashMap<>();

		arguments.forEach(argument -> {
			String arg = argument.trim().toLowerCase();

			if (arg.startsWith("export")) {
				// export database entries
				ObjectMapper mapper = new ObjectMapper();
				mapper.registerModule(new JavaTimeModule());

				Map<String, Object> struct = new HashMap<>();
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

					if (config.getTwitch() != null) {
						twitchChatBot.stop();

						twitchAuthRepository.deleteAll();
						twitchAuthRepository.saveAll(Arrays.asList(config.getTwitch()));

						twitchChatBot.initializeClients();
					}

					if (config.getSplatoon() != null) {
						splatoonLoginRepository.deleteAll();
						splatoonLoginRepository.saveAll(Arrays.asList(config.getSplatoon().clone()));
					}
				} catch (IOException e) {
					logger.error(e);
				}
			} else if (arg.startsWith("show_splatoon_url")) {
				// show splatoon 2 link
				AuthLinkCreator creator = new AuthLinkCreator();
				AuthLinkCreator.AuthParams params = creator.generateAuthenticationParams();
				URI authUri = creator.buildAuthUrl(params);

				logger.info("Auth url: \"{}\"", authUri.toString());
				logger.info("Please use parameter \"splatoon_link=LINK_FROM_SELECT_ACCOUNT_BUTTON\" on restart");
				logger.info("Verifier: \"{}\"", params.getCodeVerifier());
				logger.info("Please use parameter \"splatoon_verifier=THIS_VERIFIER\" on restart");

				extractedParams.put("stop", "true");
			} else if (arg.startsWith("splatoon_link=")) {
				// store splatoon link
				extractedParams.put("splatoon_link", argument.trim().substring("splatoon_link=".length()));
			} else if (arg.startsWith("splatoon_verifier=")) {
				// store splatoon verifier
				extractedParams.put("splatoon_verifier", argument.trim().substring("splatoon_verifier=".length()));
			}
		});

		if (extractedParams.containsKey("splatoon_link") && extractedParams.containsKey("splatoon_verifier")) {
			splatoonLoginRepository.deleteAll();
			SplatoonLogin login = splatoonLoginRepository.save(new SplatoonLogin());
			cookieHandler.generateAndStoreSessionToken(login,
					new AuthLinkCreator.AuthParams("", extractedParams.get("splatoon_verifier"), ""),
					extractedParams.get("splatoon_link"));
		}

		stop |= extractedParams.containsKey("stop");
	}

	@Scheduled(fixedRate = Integer.MAX_VALUE, initialDelay = 5000)
	private void stopIfWanted() {
		if (app != null && stop) {
			app.shutdown();
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class Config {
		private TwitchAuth[] twitch;
		private SplatoonLogin[] splatoon;
	}
}

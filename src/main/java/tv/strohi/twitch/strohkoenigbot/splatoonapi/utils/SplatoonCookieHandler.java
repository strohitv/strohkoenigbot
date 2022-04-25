package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.SplatoonLogin;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.SplatoonLoginRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.SplatNet2StatInkConfig;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SplatoonCookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private SplatoonLoginRepository splatoonLoginRepository;

	@Autowired
	public void setSplatoonLoginRepository(SplatoonLoginRepository splatoonLoginRepository) {
		this.splatoonLoginRepository = splatoonLoginRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		logger.debug("putting authentication information into request");

		List<SplatoonLogin> splatoonLogins = splatoonLoginRepository.findAll();
		SplatoonLogin login = splatoonLogins.stream().findFirst().orElse(null);
		logger.debug("found {} splatoon logins", splatoonLogins.size());
		logger.debug("using login:");
		logger.debug(login);

		if (login == null) {
			login = splatoonLoginRepository.save(new SplatoonLogin());

			sendLogs("creating new login");
		}

		List<Configuration> refreshCookieConfigurationEntries = configurationRepository.findByConfigName("refreshSplatNetCookie");
		if (login.getCookie() == null
				|| login.getCookie().isBlank()
				|| refreshCookieConfigurationEntries.size() > 0
				|| Instant.now().isAfter(login.getExpiresAt())) {
			sendLogs("refreshing cookie from splatnet2statink script");

			Configuration splatNet2StatInkLocation = configurationRepository.findByConfigName("splatNet2StatInkLocation").stream().findFirst().orElse(null);

			if (splatNet2StatInkLocation != null && new File(splatNet2StatInkLocation.getConfigValue()).exists()) {
				Configuration splatNet2StatInkCommand = configurationRepository.findByConfigName("splatNet2StatInkCommand").stream().findFirst().orElse(null);
				if (splatNet2StatInkCommand != null) {
					try {
						sendLogs("starting splatnet2statink to refresh cookie first...");
						int returnCode = Runtime.getRuntime().exec(splatNet2StatInkCommand.getConfigValue()).waitFor();
						sendLogs("success, return code: " + returnCode);
					} catch (Exception ex) {
						sendLogs("ERROR!");
						logger.error(ex);
					}
				}

				String configPath = String.format("%s%sconfig.txt", splatNet2StatInkLocation.getConfigValue(), File.separator);
				SplatNet2StatInkConfig splatNet2StatInkConfig = new ObjectMapper().readValue(new File(configPath), SplatNet2StatInkConfig.class);

				logger.debug("new auth data:");
				logger.debug(login);

				login.setCookie(splatNet2StatInkConfig.getCookie());
				login.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

				login = splatoonLoginRepository.save(login);
				configurationRepository.deleteAll(configurationRepository.findByConfigName("refreshSplatNetCookie"));
				sendLogs("done");
			} else {
				sendLogs("ERROR: splatnet2statink location not found");
			}
		}

		logger.debug("setting cookie to: 'iksm_session={}'", login.getCookie());
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);
		requestHeadersCopy.put("Cookie", Collections.singletonList(String.format("iksm_session=%s", login.getCookie())));

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
		if (responseHeaders.containsKey("set-cookie")) {
			String iksmSessionCookieText = responseHeaders.get("set-cookie").stream().filter(c -> c.contains("iksm_session")).findFirst().orElse(null);

			if (iksmSessionCookieText != null) {
				List<HttpCookie> cookies = HttpCookie.parse(iksmSessionCookieText);
				HttpCookie iksmSessionCookie = cookies.stream().findFirst().orElse(null);

				if (iksmSessionCookie != null) {
					String value = iksmSessionCookie.getValue();

					SplatoonLogin login = splatoonLoginRepository.findByCookie(value).stream().findFirst().orElse(null);
					long cookieLifeDuration = iksmSessionCookie.getMaxAge() >= 0 ? iksmSessionCookie.getMaxAge() : 31536000L;
					Instant expiresAt = Instant.now().plus(cookieLifeDuration, ChronoUnit.SECONDS);

					if (login != null && login.getExpiresAt() != null && login.getExpiresAt().isBefore(expiresAt)) {
						login.setExpiresAt(expiresAt);
						splatoonLoginRepository.save(login);
					}
				}
			}
		}
	}

	private void sendLogs(String message) {
		logger.debug(message);
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
	}
}

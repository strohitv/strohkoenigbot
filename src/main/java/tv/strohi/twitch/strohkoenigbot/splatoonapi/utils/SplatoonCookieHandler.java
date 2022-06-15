package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
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

public class SplatoonCookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private Account account;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final DiscordBot discordBot;

	private SplatoonCookieHandler(Account account, AccountRepository accountRepository, ConfigurationRepository configurationRepository, DiscordBot discordBot) {
		this.account = account;
		this.accountRepository = accountRepository;
		this.configurationRepository = configurationRepository;
		this.discordBot = discordBot;
	}

	public static SplatoonCookieHandler of(Account account, AccountRepository accountRepository, ConfigurationRepository configurationRepository, DiscordBot discordBot) {
		return new SplatoonCookieHandler(account, accountRepository, configurationRepository, discordBot);
	}

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		logger.debug("putting authentication information into request");

		List<Configuration> refreshCookieConfigurationEntries = configurationRepository.findByConfigName("refreshSplatNetCookie");
		if (account.getIsMainAccount()
				&& (
				account.getSplatoonCookie() == null
						|| account.getSplatoonCookie().isBlank()
						|| refreshCookieConfigurationEntries.size() > 0
						|| Instant.now().isAfter(account.getSplatoonCookieExpiresAt()))) {
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

				account.setSplatoonCookie(splatNet2StatInkConfig.getCookie());
				account.setSplatoonCookieExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

				account = accountRepository.save(account);
				configurationRepository.deleteAll(configurationRepository.findByConfigName("refreshSplatNetCookie"));
				sendLogs("done");
			} else {
				sendLogs("ERROR: splatnet2statink location not found");
			}
		}

		logger.debug("setting cookie to: 'iksm_session={}'", account.getSplatoonCookie());
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);
		requestHeadersCopy.put("Cookie", Collections.singletonList(String.format("iksm_session=%s", account.getSplatoonCookie())));

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders) {
		if (responseHeaders.containsKey("set-cookie")) {
			String iksmSessionCookieText = responseHeaders.get("set-cookie").stream().filter(c -> c.contains("iksm_session")).findFirst().orElse(null);

			if (iksmSessionCookieText != null) {
				List<HttpCookie> cookies = HttpCookie.parse(iksmSessionCookieText);
				HttpCookie iksmSessionCookie = cookies.stream().findFirst().orElse(null);

				if (iksmSessionCookie != null) {
					String value = iksmSessionCookie.getValue();
					account.setSplatoonCookie(value);

					long cookieLifeDuration = iksmSessionCookie.getMaxAge() >= 0 ? iksmSessionCookie.getMaxAge() : 31536000L;
					Instant expiresAt = Instant.now().plus(cookieLifeDuration, ChronoUnit.SECONDS);
					account.setSplatoonCookieExpiresAt(expiresAt);

					account = accountRepository.save(account);
				}
			}
		}
	}

	private void sendLogs(String message) {
		logger.debug(message);
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
	}
}

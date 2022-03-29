package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.SplatoonLogin;
import tv.strohi.twitch.strohkoenigbot.data.repository.SplatoonLoginRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.AuthLinkCreator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.AuthenticationData;

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

	private final AuthLinkCreator authLinkCreator = new AuthLinkCreator();
	private final Authenticator authenticator = new Authenticator();

	private SplatoonLoginRepository splatoonLoginRepository;

	@Autowired
	public void setSplatoonLoginRepository(SplatoonLoginRepository splatoonLoginRepository) {
		this.splatoonLoginRepository = splatoonLoginRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		logger.debug("putting authentication information into request");

		List<SplatoonLogin> logins = splatoonLoginRepository.findAll();
		SplatoonLogin login = logins.stream().findFirst().orElse(null);
		logger.debug("found {} splatoon logins", logins.size());
		logger.debug("using login:");
		logger.debug(login);

		if (login == null) {
			login = splatoonLoginRepository.save(new SplatoonLogin());

			logger.debug("creating new login");
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), "creating new login");
		}

		if (login.getSessionToken() == null || login.getSessionToken().isBlank()) {
			logger.warn("session token was null or blank: '{}'", login.getSessionToken());
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), String.format("session token was null or blank: '%s'", login.getSessionToken()));

			AuthLinkCreator.AuthParams params = authLinkCreator.generateAuthenticationParams();
			String authUrl = authLinkCreator.buildAuthUrl(params).toString();

			String redirectLink = ""; // Paste link here

			login = generateAndStoreSessionToken(login, params, redirectLink);
		}

		if (login.getExpiresAt() == null || Instant.now().isAfter(login.getExpiresAt())) {
			logger.debug("refreshing auth data");
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), "refreshing auth data");

			AuthenticationData authData = authenticator.refreshAccess(login.getSessionToken());

			logger.debug("new auth data:");
			logger.debug(authData);

			login.setCookie(authData.getCookie());
			login.setExpiresAt(authData.getCookieExpiresAt());
			login.setSessionToken(authData.getSessionToken());
			login.setNickname(authData.getNickname());

			login = splatoonLoginRepository.save(login);
		}

		logger.debug("setting cookie to: 'iksm_session={}'", login.getCookie());
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);
		requestHeadersCopy.put("Cookie", Collections.singletonList(String.format("iksm_session=%s", login.getCookie())));

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	@NotNull
	public SplatoonLogin generateAndStoreSessionToken(SplatoonLogin login, AuthLinkCreator.AuthParams params, String redirectLink) {
		URI link = URI.create(redirectLink);
		String session_token_code = getQueryMap(link.getFragment()).get("session_token_code");

		login.setSessionToken(authenticator.getSessionToken("71b963c1b7b6d119", session_token_code, params.getCodeVerifier()));

		login = splatoonLoginRepository.save(login);
		return login;
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

	public Map<String, String> getQueryMap(String query) {
		String[] params = query.split("&");
		Map<String, String> map = new HashMap<>();

		for (String param : params) {
			String name = param.split("=")[0];
			String value = param.split("=")[1];
			map.put(name, value);
		}
		return map;
	}
}

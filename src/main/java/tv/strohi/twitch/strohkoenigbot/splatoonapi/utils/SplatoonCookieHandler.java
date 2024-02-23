package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.AuthenticationData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SplatoonCookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private Account account;

	private final AccountRepository accountRepository;
	private final DiscordBot discordBot;

	private final Authenticator authenticator = new Authenticator();

	private SplatoonCookieHandler(Account account, AccountRepository accountRepository, DiscordBot discordBot) {
		this.account = account;
		this.accountRepository = accountRepository;
		this.discordBot = discordBot;
	}

	public static SplatoonCookieHandler of(Account account, AccountRepository accountRepository, DiscordBot discordBot) {
		return new SplatoonCookieHandler(account, accountRepository, discordBot);
	}

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		logger.debug("putting authentication information into request");
		if (account.getSplatoonCookieExpiresAt() == null || Instant.now().isAfter(account.getSplatoonCookieExpiresAt())) {
			if (account.getSplatoonSessionToken() != null && !account.getSplatoonSessionToken().isBlank()) {
				try {
					// refresh cookie
					sendLogs("refreshing auth data");

					AuthenticationData authData = authenticator.refreshAccess(account.getSplatoonSessionToken());

					account.setSplatoonCookie(authData.getCookie());
					account.setSplatoonCookieExpiresAt(authData.getCookieExpiresAt());
					account.setSplatoonSessionToken(authData.getSessionToken());
					account.setSplatoonNickname(authData.getNickname());

					account = accountRepository.save(account);
				} catch (Exception ex) {
					sendLogs("**ERROR**: could not refresh auth data because an exception occured!");
					// ERROR: Cookie refresh caused an exception -> BREAK
					resetCookieAndThrowException("could not refresh auth data because an exception occured!");
				}
			} else {
				sendLogs("**ERROR**: could not refresh auth data because session token was null!");
				// ERROR: Cannot refresh Cookie because session token is missing -> BREAK
				resetCookieAndThrowException("could not refresh auth data because session token was null!");
			}
		}

		logger.debug("setting cookie to: 'iksm_session={}'", account.getSplatoonCookie());
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);
		requestHeadersCopy.put("Cookie", Collections.singletonList(String.format("iksm_session=%s", account.getSplatoonCookie())));

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	private void resetCookieAndThrowException(String message) throws CookieRefreshException {
		account.setSplatoonCookie(null);
		account.setSplatoonCookieExpiresAt(null);

		if (account.getId() > 0 && account.getDiscordId() != null) {
			accountRepository.save(account);
		}

		throw new CookieRefreshException(account.getId(), message);
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
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, message);
	}
}

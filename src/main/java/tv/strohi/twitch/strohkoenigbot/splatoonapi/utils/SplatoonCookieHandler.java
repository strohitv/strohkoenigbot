package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.S2Tokens;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.AuthenticationData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;
import tv.strohi.twitch.strohkoenigbot.utils.ComputerNameEvaluator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.transaction.Transactional;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional
public class SplatoonCookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private Account account;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final DiscordBot discordBot;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final Authenticator authenticator = new Authenticator();

	private SplatoonCookieHandler(Account account,
								  AccountRepository accountRepository,
								  ConfigurationRepository configurationRepository,
								  DiscordBot discordBot,
								  LogSender logSender,
								  ExceptionLogger exceptionLogger) {
		this.account = account;
		this.accountRepository = accountRepository;
		this.configurationRepository = configurationRepository;
		this.discordBot = discordBot;
		this.logSender = logSender;
		this.exceptionLogger = exceptionLogger;
	}

	public static SplatoonCookieHandler of(Account account,
										   AccountRepository accountRepository,
										   ConfigurationRepository configurationRepository,
										   DiscordBot discordBot,
										   LogSender logSender,
										   ExceptionLogger exceptionLogger) {

		return new SplatoonCookieHandler(account, accountRepository, configurationRepository, discordBot, logSender, exceptionLogger);
	}

	@Override
	@Transactional
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		logger.debug("putting authentication information into request");
		if (account.getSplatoonCookieExpiresAt() == null || Instant.now().isAfter(account.getSplatoonCookieExpiresAt())) {
			if (DiscordChannelDecisionMaker.isLocalDebug()) {
				var botTokenLoadUrl = configurationRepository.findByConfigName("RequestSender_loadTokensFromProdUrl")
					.orElse(null);

				var user = configurationRepository.findAllByConfigName("uploadS3sConfigUser").stream().findFirst();
				var pass = configurationRepository.findAllByConfigName("uploadS3sConfigPassword").stream().findFirst();

				if (botTokenLoadUrl != null && user.isPresent() && pass.isPresent()) {
					HttpClient client = null;

					try {
						var authorization = String.format("Basic %s", Base64.encodeBase64String(String.format("%s:%s", user.get().getConfigValue(), pass.get().getConfigValue()).getBytes(StandardCharsets.UTF_8)));

						var request = HttpRequest.newBuilder()
							.GET()
							.uri(URI.create(botTokenLoadUrl.getConfigValue()))
							.setHeader("Authorization", authorization)
							.build();

						client = HttpClient.newBuilder()
							.connectTimeout(Duration.ofSeconds(10))
							.build();

						var response = client.send(request, HttpResponse.BodyHandlers.ofString());

						if (response.statusCode() == 200) {
							var resultStr = response.body();
							var tokens = new ObjectMapper().registerModule(new JavaTimeModule())
								.readValue(resultStr, S2Tokens.class);

							account.setSplatoonCookie(tokens.getCookie());
							account.setSplatoonCookieExpiresAt(tokens.getExpiresAt());
							account.setSplatoonNickname(tokens.getNickname());
							account.setSplatoonSessionToken(tokens.getSessionToken());

							account = accountRepository.save(account);

							logSender.queueLogs(logger, "Bot instance = %s debug = %s loaded new tokens from Prod", ComputerNameEvaluator.getComputerName(), DiscordChannelDecisionMaker.isLocalDebug());
						} else {
							logger.error("Could not load Tokens from Prod, response code {}", response.statusCode());
						}
					} catch (Exception ex) {
						exceptionLogger.logExceptionAsAttachment(logger, "Error during S3 Token loading from Prod", ex);
					} finally {
						if (client != null) {
							try {
								((AutoCloseable) client).close();
							} catch (Exception e) {
								exceptionLogger.logExceptionAsAttachment(logger, "WTF weird exception", e);
							}
						}
					}
				}
			} else if (account.getSplatoonSessionToken() != null && !account.getSplatoonSessionToken().isBlank()) {
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
	@Transactional
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

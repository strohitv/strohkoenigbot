package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.AuthenticationData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

import java.net.HttpCookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Authenticator {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final SessionTokenRetriever sessionTokenRetriever = new SessionTokenRetriever();
	private final AccountAccessTokenRetriever accountAccessTokenRetriever = new AccountAccessTokenRetriever();
	private final UserInfoRetriever userInfoRetriever = new UserInfoRetriever();
	private final FTokenRetriever fTokenRetriever = new FTokenRetriever();
	private final SplatoonTokenRetriever splatoonTokenRetriever = new SplatoonTokenRetriever();
	private final SplatoonCookieRetriever splatoonCookieRetriever = new SplatoonCookieRetriever();

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		return sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier);
	}

	public AuthenticationData getAccess(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		String sessionToken = sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier);
		return refreshAccess(sessionToken);
	}

	public AuthenticationData refreshAccess(String sessionToken) {
		logger.info("refreshing cookie of session token: {}", sessionToken);

		int now = (int) (new Date().getTime() / 1000);
		String guid = UUID.randomUUID().toString();

		String accountAccessToken = accountAccessTokenRetriever.getAccountAccessToken(sessionToken);
		logger.info("accountAccessToken");
		logger.info(accountAccessToken);

		UserInfo userInfo = userInfoRetriever.getUserInfo(accountAccessToken);
		logger.info("userInfo");
		logger.info(userInfo);

		FParamLoginResult fTokenNso = fTokenRetriever.getFToken(accountAccessToken, guid, now, "nso");
		logger.info("fTokenNso");
		logger.info(fTokenNso);


		String gameWebToken = splatoonTokenRetriever.doSplatoonAppLogin(userInfo, fTokenNso);
		logger.info("gameWebToken");
		logger.info(gameWebToken);

		FParamLoginResult fTokenApp = fTokenRetriever.getFToken(gameWebToken, guid, now, "app");
		logger.info("fTokenApp");
		logger.info(fTokenApp);


		String splatoonAccessToken = splatoonTokenRetriever.getSplatoonAccessToken(gameWebToken, fTokenApp);
		logger.info("splatoonAccessToken");
		logger.info(splatoonAccessToken);

		String splatoonCookie = splatoonCookieRetriever.getSplatoonCookie(splatoonAccessToken);
		logger.info("splatoonCookie");
		logger.info(splatoonCookie);


		List<HttpCookie> cookies = HttpCookie.parse(splatoonCookie);
		HttpCookie iksmSessionCookie = cookies.stream().findFirst().orElse(null);
		logger.info("iksmSessionCookie");
		logger.info(iksmSessionCookie);


		if (iksmSessionCookie != null) {
			logger.info("worked");
			String value = iksmSessionCookie.getValue();
			long cookieLifeDuration = iksmSessionCookie.getMaxAge() >= 0 ? iksmSessionCookie.getMaxAge() : 31536000L;

			Instant expiresAt = Instant.now().plus(cookieLifeDuration, ChronoUnit.SECONDS);
			return new AuthenticationData(userInfo.getNickname(), value, expiresAt, sessionToken);
		} else {
			logger.error("exepction");
			throw new RuntimeException("Splatoon 2 cookie could not be loaded");
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.AuthenticationData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.UserInfo;

import java.net.HttpCookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Authenticator {
	private final SessionTokenRetriever sessionTokenRetriever = new SessionTokenRetriever();
	private final AccountAccessTokenRetriever accountAccessTokenRetriever = new AccountAccessTokenRetriever();
	private final UserInfoRetriever userInfoRetriever = new UserInfoRetriever();
	private final FTokenRetriever fTokenRetriever = new FTokenRetriever();
	private final SplatoonTokenRetriever splatoonTokenRetriever = new SplatoonTokenRetriever();
	private final SplatoonCookieRetriever splatoonCookieRetriever = new SplatoonCookieRetriever();

	public AuthenticationData getAccess(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		String sessionToken = sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier);
		return refreshAccess(sessionToken);
	}

	public AuthenticationData refreshAccess(String sessionToken) {
		int now = (int) (new Date().getTime() / 1000);
		String guid = UUID.randomUUID().toString();

		String accountAccessToken = accountAccessTokenRetriever.getAccountAccessToken(sessionToken);
		UserInfo userInfo = userInfoRetriever.getUserInfo(accountAccessToken);
		FParamLoginResult fTokenNso = fTokenRetriever.getFToken(accountAccessToken, guid, now, "nso");

		String gameWebToken = splatoonTokenRetriever.doSplatoonAppLogin(userInfo, fTokenNso);
		FParamLoginResult fTokenApp = fTokenRetriever.getFToken(gameWebToken, guid, now, "app");

		String splatoonAccessToken = splatoonTokenRetriever.getSplatoonAccessToken(gameWebToken, fTokenApp);
		String splatoonCookie = splatoonCookieRetriever.getSplatoonCookie(splatoonAccessToken);

		List<HttpCookie> cookies = HttpCookie.parse(splatoonCookie);
		HttpCookie iksmSessionCookie = cookies.stream().findFirst().orElse(null);

		if (iksmSessionCookie != null) {
			String value = iksmSessionCookie.getValue();
			long cookieLifeDuration = iksmSessionCookie.getMaxAge() >= 0 ? iksmSessionCookie.getMaxAge() : 31536000L;

			Instant expiresAt = Instant.now().plus(cookieLifeDuration, ChronoUnit.SECONDS);
			return new AuthenticationData(userInfo.getNickname(), value, expiresAt, sessionToken);
		} else {
			throw new RuntimeException("Splatoon 2 cookie could not be loaded");
		}
	}
}

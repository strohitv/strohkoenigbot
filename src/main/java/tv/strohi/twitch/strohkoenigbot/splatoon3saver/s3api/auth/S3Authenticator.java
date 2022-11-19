package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

public class S3Authenticator {
	private static final long SPLATOON3_TOKEN_REQUEST_ID = 4834290508791808L;

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final SessionTokenRetriever sessionTokenRetriever = new SessionTokenRetriever();
	private final AccountAccessTokenRetriever accountAccessTokenRetriever = new AccountAccessTokenRetriever();
	private final UserInfoRetriever userInfoRetriever = new UserInfoRetriever();
	private final FTokenRetriever fTokenRetriever = new FTokenRetriever();
	private final SplatoonTokenRetriever splatoonTokenRetriever = new SplatoonTokenRetriever();
	private final BulletTokenLoader bulletTokenLoader = new BulletTokenLoader();

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		return sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier);
	}

	public S3AuthenticationData refreshAccess(String sessionToken) {
		logger.info("refreshing cookie of session token: {}", sessionToken);

		String accountAccessToken = accountAccessTokenRetriever.getAccountAccessToken(sessionToken);
		logger.info("accountAccessToken");
		logger.info(accountAccessToken);

		UserInfo userInfo = userInfoRetriever.getUserInfo(accountAccessToken);
		logger.info("userInfo");
		logger.info(userInfo);

		FParamLoginResult fTokenNso = fTokenRetriever.getFTokenFromIminkApi(accountAccessToken, 1);
		logger.info("fTokenNso");
		logger.info(fTokenNso);


		String idToken = splatoonTokenRetriever.doSplatoonAppLogin(userInfo, fTokenNso, accountAccessToken);
		logger.info("gameWebToken");
		logger.info(idToken);

		FParamLoginResult fTokenApp = fTokenRetriever.getFTokenFromIminkApi(idToken, 2);
		logger.info("fTokenApp");
		logger.info(fTokenApp);

		// TODO apparently this is the gtoken???
		String gToken = splatoonTokenRetriever.getSplatoonAccessToken(idToken, fTokenApp, accountAccessToken, SPLATOON3_TOKEN_REQUEST_ID);
		logger.info("gToken");
		logger.info(gToken);

		// TODO steps to bulletToken are new
		String bulletToken = bulletTokenLoader.getBulletToken(gToken, userInfo);
		logger.info("bulletToken");
		logger.info(bulletToken);

		return S3AuthenticationData.builder()
				.gToken(gToken)
				.bulletToken(bulletToken)
				.build();
	}
}

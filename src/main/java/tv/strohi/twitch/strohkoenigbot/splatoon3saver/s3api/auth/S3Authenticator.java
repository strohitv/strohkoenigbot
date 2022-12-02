package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

@Component
@RequiredArgsConstructor
public class S3Authenticator {
	public static final String SPLATOON3_WEBVIEWVERSION_CONFIG_NAME = "Splatoon3_WebViewVersion";
	private static final long SPLATOON3_TOKEN_REQUEST_ID = 4834290508791808L;

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final ConfigurationRepository configurationRepository;

	private final LogSender logSender;

	private final SessionTokenRetriever sessionTokenRetriever = new SessionTokenRetriever();
	private final AccountAccessTokenRetriever accountAccessTokenRetriever = new AccountAccessTokenRetriever();
	private final UserInfoRetriever userInfoRetriever = new UserInfoRetriever();
	private final FTokenRetriever fTokenRetriever = new FTokenRetriever();
	private final SplatoonTokenRetriever splatoonTokenRetriever = new SplatoonTokenRetriever();
	private final WebViewVersionLoader webViewVersionLoader = new WebViewVersionLoader();
	private final BulletTokenLoader bulletTokenLoader;

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		return sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier);
	}

	public S3AuthenticationData refreshAccess(String sessionToken) {
		logger.info("refreshing cookie of session token: {}", sessionToken);

		String accountAccessToken = accountAccessTokenRetriever.getAccountAccessToken(sessionToken);
		logger.debug("accountAccessToken");
		logger.debug(accountAccessToken);

		UserInfo userInfo = userInfoRetriever.getUserInfo(accountAccessToken);
		logger.debug("userInfo");
		logger.debug(userInfo);

		FParamLoginResult fTokenNso = fTokenRetriever.getFTokenFromIminkApi(accountAccessToken, 1);
		logger.debug("fTokenNso");
		logger.debug(fTokenNso);


		String idToken = splatoonTokenRetriever.doSplatoonAppLogin(userInfo, fTokenNso, accountAccessToken);
		logger.debug("gameWebToken");
		logger.debug(idToken);

		FParamLoginResult fTokenApp = fTokenRetriever.getFTokenFromIminkApi(idToken, 2);
		logger.debug("fTokenApp");
		logger.debug(fTokenApp);

		// TODO apparently this is the gtoken???
		String gToken = splatoonTokenRetriever.getSplatoonAccessToken(idToken, fTokenApp, accountAccessToken, SPLATOON3_TOKEN_REQUEST_ID);
		logger.debug("gToken");
		logger.debug(gToken);

		String webViewVersion = webViewVersionLoader.refreshWebViewVersion(gToken);
		logger.debug("webViewVersion");
		logger.debug(webViewVersion);

		if (webViewVersion != null) {
			Configuration webViewConfigs = configurationRepository.findByConfigName(SPLATOON3_WEBVIEWVERSION_CONFIG_NAME).stream()
					.findFirst()
					.orElse(new Configuration(0L, SPLATOON3_WEBVIEWVERSION_CONFIG_NAME, null));

			if (!webViewVersion.equals(webViewConfigs.getConfigValue())) {
				webViewConfigs.setConfigValue(webViewVersion);

				configurationRepository.save(webViewConfigs);
				logSender.sendLogs(logger, String.format("Saved newest WebViewVersion: **%s**", webViewVersion));
			}
		}

		// TODO steps to bulletToken are new
		String bulletToken = bulletTokenLoader.getBulletToken(gToken, userInfo);
		logger.debug("bulletToken");
		logger.debug(bulletToken);

		logger.info("done refreshing cookie of session token: {}", sessionToken);

		return S3AuthenticationData.builder()
				.gToken(gToken)
				.bulletToken(bulletToken)
				.build();
	}
}

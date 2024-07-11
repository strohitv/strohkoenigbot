package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3S3sRunner;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ConfigFileConnector;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class S3Authenticator {
	public static final String SPLATOON3_WEBVIEWVERSION_CONFIG_NAME = "Splatoon3_WebViewVersion";
	public static final String SPLATOON3_NSOAPPVERSION_CONFIG_NAME = "Splatoon3_NsoAppVersion";
	private static final long SPLATOON3_TOKEN_REQUEST_ID = 4834290508791808L;

	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final ConfigurationRepository configurationRepository;

	private final LogSender logSender;

	private final SessionTokenRetriever sessionTokenRetriever = new SessionTokenRetriever();
	private final AccountAccessTokenRetriever accountAccessTokenRetriever = new AccountAccessTokenRetriever();
	private final UserInfoRetriever userInfoRetriever = new UserInfoRetriever();
	private final FTokenRetriever fTokenRetriever = new FTokenRetriever();
	private final NsoAppVersionRetriever nsoAppVersionRetriever = new NsoAppVersionRetriever();
	private final SplatoonTokenRetriever splatoonTokenRetriever = new SplatoonTokenRetriever();
	private final WebViewVersionLoader webViewVersionLoader = new WebViewVersionLoader();
	private final BulletTokenLoader bulletTokenLoader;

	private final S3S3sRunner s3sRunner;
	private final ConfigFileConnector configFileConnector;

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		var nsoAppVersion = loadNsoAppVersion();

		return sessionTokenRetriever.getSessionToken(clientId, sessionTokenCode, sessionTokenCodeVerifier, nsoAppVersion);
	}

	public S3AuthenticationData refreshAccess(String sessionToken) {
		String gToken;
		String bulletToken;

		try {
			logger.info("refreshing cookie of session token: {}", sessionToken);

			var nsoAppVersion = loadNsoAppVersion();

			String accountAccessToken = accountAccessTokenRetriever.getAccountAccessToken(sessionToken, nsoAppVersion);
			logger.debug("accountAccessToken");
			logger.debug(accountAccessToken);

			UserInfo userInfo = userInfoRetriever.getUserInfo(accountAccessToken, nsoAppVersion);
			logger.debug("userInfo");
			logger.debug(userInfo);

			FParamLoginResult fTokenNso = fTokenRetriever.getFTokenFromIminkApi(accountAccessToken, 1);
			logger.debug("fTokenNso");
			logger.debug(fTokenNso);

			if (fTokenNso != null) {
				String idToken = splatoonTokenRetriever.doSplatoonAppLogin(userInfo, fTokenNso, accountAccessToken, nsoAppVersion);
				logger.debug("gameWebToken");
				logger.debug(idToken);

				FParamLoginResult fTokenApp = fTokenRetriever.getFTokenFromIminkApi(idToken, 2);
				logger.debug("fTokenApp");
				logger.debug(fTokenApp);

				gToken = splatoonTokenRetriever.getSplatoonAccessToken(idToken, fTokenApp, accountAccessToken, SPLATOON3_TOKEN_REQUEST_ID, nsoAppVersion);
				logger.debug("gToken");
				logger.debug(gToken);

				String webViewVersion = webViewVersionLoader.refreshWebViewVersion(gToken);
				logger.debug("webViewVersion");
				logger.debug(webViewVersion);

				if (webViewVersion != null) {
					Configuration webViewConfigs = configurationRepository.findAllByConfigName(SPLATOON3_WEBVIEWVERSION_CONFIG_NAME).stream()
						.findFirst()
						.orElse(new Configuration(0L, SPLATOON3_WEBVIEWVERSION_CONFIG_NAME, null));

					if (!webViewVersion.equals(webViewConfigs.getConfigValue())) {
						webViewConfigs.setConfigValue(webViewVersion);

						configurationRepository.save(webViewConfigs);
						logSender.sendLogs(logger, String.format("Saved newest WebViewVersion: **%s**", webViewVersion));
					}
				}

				bulletToken = bulletTokenLoader.getBulletToken(gToken, userInfo);
				logger.debug("bulletToken");
				logger.debug(bulletToken);

				logger.info("done refreshing cookie of session token: {}", sessionToken);

				return S3AuthenticationData.builder()
					.gToken(gToken)
					.bulletToken(bulletToken)
					.build();
			}
		} catch (RuntimeException ex) {
			logger.error(ex);
		}

		// regular attempt did not work
		return tryGetTokensFromS3s();
	}

	private @Nullable String loadNsoAppVersion() {
		var nsoAppVersion = nsoAppVersionRetriever.getNsoAppVersion();

		if (nsoAppVersion != null) {
			Configuration nsoAppVersionConfigs = configurationRepository.findAllByConfigName(SPLATOON3_NSOAPPVERSION_CONFIG_NAME).stream()
				.findFirst()
				.orElse(new Configuration(0L, SPLATOON3_NSOAPPVERSION_CONFIG_NAME, null));

			if (!nsoAppVersion.equals(nsoAppVersionConfigs.getConfigValue())) {
				nsoAppVersionConfigs.setConfigValue(nsoAppVersion);

				configurationRepository.save(nsoAppVersionConfigs);
				logSender.sendLogs(logger, String.format("Saved newest NsoAppVersion: **%s**", nsoAppVersion));
			}
		}

		return nsoAppVersion;
	}

	private S3AuthenticationData tryGetTokensFromS3s() {
		var s3sScriptCommand = configurationRepository.findAllByConfigName("s3sScript").stream()
			.map(Configuration::getConfigValue)
			.findFirst()
			.orElse(null);
		var s3sLocation = configurationRepository.findAllByConfigName("s3sLocation").stream()
			.map(Configuration::getConfigValue)
			.findFirst()
			.orElse(null);

		if (s3sScriptCommand != null && s3sLocation != null) {
			logger.info("Could not refresh gtoken easily, falling back to s3s gtoken attempt...");
			// fallback: try to load from s3s
			// run s3s
			var token = String.format("S3Authenticator_%s", Instant.now());
			s3sRunner.runS3S(token);
			Boolean s3sResult;

			// wait for s3s to finish
			while ((s3sResult = s3sRunner.getResult(token)) == null) {
				try {
					logger.info("still waiting for s3s run with token '{}' to succeed...", token);
					Thread.sleep(5000);
				} catch (Exception ignored) {
				}
			}

			if (s3sResult) {
				// load tokens from s3s config file
				var s3sConfigFile = configFileConnector.readConfigFile(s3sLocation);
				logSender.sendLogs(logger, "s3s fallback for gtoken succeeded!");

				return S3AuthenticationData.builder()
					.gToken(s3sConfigFile.getGtoken())
					.bulletToken(s3sConfigFile.getBullettoken())
					.build();
			} else {
				throw new RuntimeException("Could not load tokens from Config, result was false!");
			}
		} else {
			throw new RuntimeException("Could fall back to s3s token refresh, script command or location were not provided!");
		}
	}
}

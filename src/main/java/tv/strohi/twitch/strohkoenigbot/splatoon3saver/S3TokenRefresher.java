package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.SplatNet3DataController;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3TokenRefresher {
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	private final S3ApiQuerySender requestSender;
	private final S3S3sRunner s3S3sRunner;

	private final ObjectMapper mapper;
	private final TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
	};

	public void refreshToken() {
		accountRepository.findByEnableSplatoon3(true).stream()
			.findFirst()
			.ifPresent(account -> {
				try {
					var endTime = Instant.now().plus(4, ChronoUnit.MINUTES);
					var runToken = String.format("s3s_%s", Instant.now());
					s3S3sRunner.runS3S(runToken);

					while (endTime.isAfter(Instant.now()) && (s3S3sRunner.getResult(runToken) == null || !s3S3sRunner.getResult(runToken))) {
						Thread.sleep(1000);
					}

					if (s3S3sRunner.getResult(runToken) == null || !s3S3sRunner.getResult(runToken)) {
						logSender.sendLogs(log, String.format("### ERROR during S3TokenRefresh\ns3s with Token '%s' did not finish in time or it failed!", runToken));
						return;
					}

					var s3sLocation = configurationRepository.findAllByConfigName("s3sLocation").stream().findFirst();
					if (s3sLocation.isEmpty() || !new File(Paths.get(s3sLocation.get().getConfigValue(), "config.txt").toString()).exists()) {
						logSender.sendLogs(log, "### ERROR during S3TokenRefresh\n'config.txt' file could not be found!");
						return;
					}

					var s3sConfigFile = new File(Paths.get(s3sLocation.get().getConfigValue(), "config.txt").toString());
					var s3sConfig = mapper.readValue(s3sConfigFile, ConfigFile.class);

					if (s3sConfig == null
						|| s3sConfig.getGtoken() == null || s3sConfig.getGtoken().isBlank()
						|| s3sConfig.getBullettoken() == null || s3sConfig.getBullettoken().isBlank()) {
						logSender.sendLogs(log, "### ERROR during S3TokenRefresh\n'config.txt' file did not contain gtoken or bullettoken!");
						return;
					}

					var currentToken = mapper.readValue(new String(Base64.getDecoder().decode(account.getGTokenSplatoon3().split("\\.")[1])), typeRef);
					var newToken = mapper.readValue(new String(Base64.getDecoder().decode(s3sConfig.getGtoken().split("\\.")[1])), typeRef);

					var currentTokenExp = (Integer) currentToken.getOrDefault("exp", null);
					var newTokenExp = (Integer) newToken.getOrDefault("exp", null);

					if (currentTokenExp == null || newTokenExp == null) {
						logSender.sendLogs(log, String.format("### ERROR during S3TokenRefresh\nOne of the two exp values of the token was `null`!\n- current token exp: `%s`\n- new token exp: `%s`", currentTokenExp, newTokenExp));
						return;
					}

					if (newTokenExp > currentTokenExp) {
						var accountWithNewTokens = account.toBuilder().gTokenSplatoon3(s3sConfig.getGtoken()).bulletTokenSplatoon3(s3sConfig.getBullettoken()).build();

						var homeResponse = requestSender.queryS3Api(accountWithNewTokens, S3RequestKey.Home, "naCountry", "US");

						if (homeResponse != null && homeResponse.contains("\"data\":{\"currentPlayer\"")) {
							SplatNet3DataController.setNextTimeTokenExpires(newTokenExp);
							accountRepository.save(accountWithNewTokens);
							log.info("S3TokenRefresher successful.");
						} else {
							logSender.sendLogs(log, "### ERROR during S3TokenRefresh\nHomepage response did not load successfully, tokens were invalid.");
						}
					} else {
						logSender.sendLogs(log, "### ERROR during S3TokenRefresh\n S3TokenRefresher did not find a newer token!");
					}

				} catch (Exception e) {
					logSender.sendLogs(log, "An exception occurred during S3TokenRefresh\nSee logs for details!");
					exceptionLogger.logException(log, e);
				}
			});
	}
}

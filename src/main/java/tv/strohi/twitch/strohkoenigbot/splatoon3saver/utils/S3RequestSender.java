package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3TokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Component
@RequiredArgsConstructor
public class S3RequestSender {
	public static final String SPLATNET_3_MAX_RETRIES_CONFIG_NAME = "SplatNet3MaxRetryCount";
	private static final int SPLATNET_3_DEFAULT_MAX_RETRIES = 3;

	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final ImageService imageService;

	private final ConfigurationRepository configurationRepository;

	@Getter
	private final Map<Integer, Integer> responseCodes = new HashMap<>();

	public String sendRequestAndParseGzippedJson(HttpClient client, HttpRequest request) {
		String body = "";
		int retryCount = 0;

		var maxRetriesConfig = configurationRepository.findByConfigName(SPLATNET_3_MAX_RETRIES_CONFIG_NAME)
			.orElse(configurationRepository.save(Configuration.builder().configName(SPLATNET_3_MAX_RETRIES_CONFIG_NAME).configValue(String.format("%d", SPLATNET_3_DEFAULT_MAX_RETRIES)).build()));

		var maxRetries = parseMaxRetryCount(maxRetriesConfig.getConfigValue());

		while (retryCount < maxRetries) {
			retryCount++;

			try {
				logger.debug("RequestSender sending new request to '{}'", request.uri().toString());
				HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

				logger.debug("got response with status code {}:", response.statusCode());

				var responseCodeNumber = responseCodes.getOrDefault(response.statusCode(), 0);
				responseCodeNumber++;
				responseCodes.put(response.statusCode(), responseCodeNumber);

				if (response.statusCode() < 300) {
					body = new String(response.body());

					if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
						body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
					}

					try {
						imageService.shortenJson(body);
					} catch (Exception ignored) {
					}

					return body;
				} else {
					var sleepTime = 5000;
					if (response.statusCode() == 401) {
						logSender.sendLogs(logger, "Reset token duration because a 401 error was received.");

						var tokenExpirationConfig = configurationRepository.findByConfigName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME)
							.orElse(Configuration.builder().configName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME).configValue(String.format("%d", Instant.now().getEpochSecond())).build());
						tokenExpirationConfig.setConfigValue(String.format("%d", Instant.now().getEpochSecond()));
						configurationRepository.save(tokenExpirationConfig);

						return null;
					} else if (response.statusCode() < 500) {
//						logSender.sendLogs(logger, String.format("Request could not be fulfilled.\nRequest:\n```\n%s\n```", serializeRequest(request)));
						logSender.sendLogs(logger, String.format("Request could not be fulfilled.\nResponse:\n```\n%s\n```", serializeResponse(response)));
						sleepTime *= 3;
					} 

					try {
						Thread.sleep(sleepTime);
					} catch (InterruptedException ignored) {
					}
				}
			} catch (IOException | InterruptedException e) {
				if (e instanceof IOException && e.getCause() != null && e.getCause() instanceof CookieRefreshException) {
					logSender.sendLogs(logger, "The cookie for account wasn't valid anymore and no session token has been set!");

					return null;
				} else {
					// log and retry in a second
					logSender.sendLogs(logger, "exception while sending request, retrying...");
					logger.error("response body: '{}'", body);

					exceptionLogger.logException(logger, e);

					logger.error("retry count: {} of 4", retryCount);

					try {
						Thread.sleep(5000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}

		logSender.sendLogs(logger, String.format("S3RequestSender failed %d times in a row.", maxRetries));
		return null;
	}

	private String serializeRequest(HttpRequest request) {
		StringBuilder result = new StringBuilder("URL: ").append(request.uri()).append("\nHeaders:");

		for (var header : request.headers().map().keySet()) {
			request.headers().map().get(header).forEach(value -> result.append("\n").append(header).append(": ").append(value));
		}

		return result.toString();
	}

	private int parseMaxRetryCount(String configValue) {
		int retryCount = SPLATNET_3_DEFAULT_MAX_RETRIES;

		try {
			retryCount = Integer.parseInt(configValue);
		} catch (Exception ignored) {
		}

		if (retryCount <= 0) {
			retryCount = 1;
		}

		return retryCount;
	}

	private String serializeResponse(HttpResponse<byte[]> response) {
		StringBuilder result = new StringBuilder("URL: ").append(response.uri()).append("\nStatuscode: ").append(response.statusCode()).append("\nHeaders:");

		for (var header : response.headers().map().keySet()) {
			response.headers().map().get(header).forEach(value -> result.append("\n").append(header).append(": ").append(value));
		}

		if (response.body() != null) {
			var body = new String(response.body());

			if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
				try {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				} catch (IOException e) {
					logger.error(e);
				}
			}

			result.append("\nBody: \n").append(body);
		}

		return result.toString();
	}
}

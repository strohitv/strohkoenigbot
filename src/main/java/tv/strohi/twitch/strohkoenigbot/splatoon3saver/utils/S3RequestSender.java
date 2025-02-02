package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

@Component
@RequiredArgsConstructor
public class S3RequestSender {
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final ImageService imageService;

	public String sendRequestAndParseGzippedJson(HttpClient client, HttpRequest request) {
		String body = "";
		int retryCount = 0;

		while (retryCount < 5) {
			try {
				logger.debug("RequestSender sending new request to '{}'", request.uri().toString());
				HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

				logger.debug("got response with status code {}:", response.statusCode());

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
					logSender.sendLogs(logger, "Request could not be fulfilled.\nRequest:");
					logSender.sendLogs(logger, String.format("```\n%s\n```", serializeRequest(request)));
					logSender.sendLogs(logger, "Response:");
					logSender.sendLogs(logger, String.format("```\n%s\n```", serializeResponse(response)));

					try {
						Thread.sleep(5000);
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

					retryCount++;
					logger.error("retry count: {} of 4", retryCount);

					try {
						Thread.sleep(5000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}

		logSender.sendLogs(logger, "S3RequestSender failed 5 times in a row.");
		return null;
	}

	private String serializeRequest(HttpRequest request) {
		StringBuilder result = new StringBuilder("URL: ").append(request.uri()).append("\nHeaders:");

		for (var header : request.headers().map().keySet()) {
			request.headers().map().get(header).forEach(value -> result.append("\n").append(header).append(": ").append(value));
		}

		return result.toString();
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

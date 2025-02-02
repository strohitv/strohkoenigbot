package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final ImageService imageService;

	private final ObjectMapper mapper;

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
					} catch (Exception ignored) {}

					return body;
				} else {
					logSender.sendLogs(logger, "request:");
					logSender.sendLogs(logger, String.format("```\n%s\n```", serializeObject(request)));
					logSender.sendLogs(logger, "response:");
					logSender.sendLogs(logger, String.format("```\n%s\n```", serializeObject(response)));

					return null;
				}
			} catch (IOException | InterruptedException e) {
				if (e instanceof IOException && e.getCause() != null && e.getCause() instanceof CookieRefreshException) {
					logSender.sendLogs(logger, "The cookie for account wasn't valid anymore and no session token has been set!");

					return null;
				} else {
					// log and retry in a second
					logger.error("exception while sending request, retrying...");
					logger.error("response body: '{}'", body);

					logger.error(e);

					retryCount++;
					logger.error("retry count: {} of 4", retryCount);

					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}

		logSender.sendLogs(logger, "S3RequestSender failed 5 times in a row.");
		return null;
	}

	private String serializeObject(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "";
		}
	}
}

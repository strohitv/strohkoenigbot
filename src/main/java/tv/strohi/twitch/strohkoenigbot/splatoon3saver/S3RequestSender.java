package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

@Component
public class S3RequestSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	public String queryS3Api(String bulletToken, String gToken, String actionHash) {
		return queryS3Api(bulletToken, gToken, actionHash, null);
	}

	public String queryS3Api(String bulletToken, String gToken, String actionHash, String matchId) {
		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(new S3CookieHandler(gToken))
				.build();

		String body = String.format("{\"variables\":{},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", actionHash);

		if (matchId != null) {
			if (S3RequestKey.GameDetail.getKey().equals(actionHash)) {
				body = String.format("{\"variables\":{\"vsResultId\":\"%s\"},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", matchId, actionHash);
			} else {
				// salmon run
				body = String.format("{\"variables\":{\"coopHistoryDetailId\":\"%s\"},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", matchId, actionHash);
			}
		}

		String address = "https://api.lp1.av5ja.srv.nintendo.net/api/graphql";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("Authorization", String.format("Bearer %s", bulletToken))
				.setHeader("Accept-Language", "en-US")
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
				.setHeader("X-Web-View-Ver", "1.0.0-d3a90678")
				.setHeader("Content-Type", "application/json")
				.setHeader("Accept", "*/*")
				.setHeader("Origin", "https://api.lp1.av5ja.srv.nintendo.net")
				.setHeader("X-Requested-With", String.format("Bearer %s", bulletToken))
				.setHeader("Referer", "https://api.lp1.av5ja.srv.nintendo.net/?lang=en-US&na_country=US&na_lang=en-US")
				.setHeader("Accept-Encoding", "gzip, deflate")
				.build();

		return sendRequestAndParseGzippedJson(client, request);
	}

	private String sendRequestAndParseGzippedJson(HttpClient client, HttpRequest request) {
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

					return body;
				} else {
					logger.info("request:");
					logger.info(request);
					logger.info("response:");
					logger.info(response);

					return null;
				}
			} catch (IOException | InterruptedException e) {
				if (e instanceof IOException && e.getCause() != null && e.getCause() instanceof CookieRefreshException) {
					logger.error("The cookie for account wasn't valid anymore and no session token has been set!");

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

		return null;
	}
}

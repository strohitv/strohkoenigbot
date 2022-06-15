package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

@Component
public class RequestSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final String host = "https://app.splatoon2.nintendo.net";
	private final String appUniqueId = "32449507786579989235";

	private AuthenticatedHttpClientCreator clientCreator;

	@Autowired
	public void setClientCreator(AuthenticatedHttpClientCreator clientCreator) {
		this.clientCreator = clientCreator;
	}

	private final ObjectMapper mapper = new ObjectMapper();

	public <T> T querySplatoonApiForAccount(Account account, String path, Class<T> valueType) {
		TimeZone tz = TimeZone.getDefault();
		int offset = tz.getOffset(new Date().getTime()) / 1000 / 60;

		String address = host + path;

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("x-unique-id", appUniqueId)
				.setHeader("x-requested-with", "XMLHttpRequest")
				.setHeader("x-timezone-offset", String.format("%d", offset))
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
				.setHeader("Accept", "*/*")
				.setHeader("Referer", "https://app.splatoon2.nintendo.net/home")
				.setHeader("Accept-Encoding", "gzip, deflate")
				.setHeader("Accept-Language", "en-US")
				.build();

		return sendRequestAndParseGzippedJson(account, request, valueType);
	}

	private <T> T sendRequestAndParseGzippedJson(Account account, HttpRequest request, Class<T> valueType) {
		String body = "";

		try {
			logger.debug("RequestSender sending new request to '{}'", request.uri().toString());
			HttpResponse<byte[]> response = clientCreator.of(account).send(request, HttpResponse.BodyHandlers.ofByteArray());

			logger.debug("got response with status code {}:", response.statusCode());

			if (response.statusCode() < 300) {
				body = new String(response.body());

				if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				}

				return mapper.readValue(body, valueType);
			} else {
				logger.info("request:");
				logger.info(request);
				logger.info("response:");
				logger.info(response);
			}
		} catch (IOException | InterruptedException e) {
			logger.error("exception while sending request");
			logger.error("response body: '{}'", body);

			logger.error(e);
		}

		return null;
	}
}

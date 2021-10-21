package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
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

	private HttpClient client;

	@Autowired
	public void setClient(HttpClient client) {
		this.client = client;
	}

	private final ObjectMapper mapper = new ObjectMapper();

	public <T> T querySplatoonApi(String path , Class<T> valueType) {
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
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.2; Pixel Build/NJH47D; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/59.0.3071.125 Mobile Safari/537.36")
				.setHeader("Accept", "*/*")
				.setHeader("Referer", "https://app.splatoon2.nintendo.net/home")
				.setHeader("Accept-Encoding", "gzip, deflate")
				.setHeader("Accept-Language", "en-US")
				.build();

		return sendRequestAndParseGzippedJson(request, valueType);
	}

	private <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
		try {
			logger.info("RequestSender sending new request to '{}'", request.uri().toString());
			logger.info(request);
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

			logger.info("got response with status code {}:", response.statusCode());
			logger.info(response);

			if (response.statusCode() < 300) {
				String body = new String(response.body());

				if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				}

				logger.info("response body: '{}'", body);

				return mapper.readValue(body, valueType);
			}
		} catch (IOException | InterruptedException e) {
			logger.error("exception while sending request");
			logger.error(e);
		}

		return null;
	}
}

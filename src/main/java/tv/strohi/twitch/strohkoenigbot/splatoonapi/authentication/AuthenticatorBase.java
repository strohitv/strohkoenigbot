package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

public abstract class AuthenticatorBase {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	protected final String nsoapp_version = "1.13.2";

	protected final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();
	protected final String accountsHost = "https://accounts.nintendo.com";

	protected final ObjectMapper mapper = new ObjectMapper();

	protected final <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
		try {
			logger.info("sending new request to '{}'", request.uri().toString());
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

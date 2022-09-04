package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

public abstract class AuthenticatorBase {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	protected static final String nsoAppVersion;
	private static final String nsoAppFallbackVersion = "2.2.0";
	private static final String nsoAppVersionHistoryUrl = "https://www.nintendo.co.jp/support/app/nintendo_switch_online_app/index.html";

	protected final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();
	protected final String accountsHost = "https://accounts.nintendo.com";

	protected final ObjectMapper mapper = new ObjectMapper();

	static {
		nsoAppVersion = loadCurrentAndroidAppVersion();
	}

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
			} else {
				logger.info("request:");
				logger.info(request);
				logger.info("response:");
				logger.info(response);

				String body = new String(response.body());
				if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				}
				logger.info(body);

				return null;
			}
		} catch (IOException | InterruptedException e) {
			logger.error("exception while sending request");
			logger.error(e);
		}

		return null;
	}

	private static String loadCurrentAndroidAppVersion() {
		String result = nsoAppFallbackVersion;

		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(nsoAppVersionHistoryUrl))
					.GET() // GET is default
					.build();

			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString());

			result = Arrays.stream(response.body().split("</span>"))
					.filter(str -> str.contains("Ver."))
					.map(str -> Arrays.stream(str.split("Ver\\."))
							.reduce((first, second) -> second)
							.orElse(null))
					.filter(Objects::nonNull)
					.map(str -> Arrays.stream(str.trim().split(" "))
							.findFirst()
							.orElse(null))
					.filter(Objects::nonNull)
					.map(String::trim)
					.findFirst()
					.orElse(nsoAppFallbackVersion);
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}

		return result;
	}
}

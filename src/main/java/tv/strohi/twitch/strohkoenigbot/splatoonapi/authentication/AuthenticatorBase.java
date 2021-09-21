package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

public abstract class AuthenticatorBase {
	protected final String nsoapp_version = "1.12.0";

	protected final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();
	protected final String accountsHost = "https://accounts.nintendo.com";

	protected final ObjectMapper mapper = new ObjectMapper();

	protected final <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
		try {
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			System.out.println(response);
			System.out.println(new String(response.body()));

			if (response.statusCode() < 300) {
				String body = new String(response.body());

				if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				}

				return mapper.readValue(body, valueType);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}
}

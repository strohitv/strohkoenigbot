package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonMatchResultsCollection;

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
public class ResultsLoader {
	private final String appUniqueId = "32449507786579989235";

	private HttpClient client;

	@Autowired
	public void setClient(HttpClient client) {
		this.client = client;
	}

	private final ObjectMapper mapper = new ObjectMapper();

	public SplatoonMatchResultsCollection getGameResults() {
		TimeZone tz = TimeZone.getDefault();
		int offset = tz.getOffset(new Date().getTime()) / 1000 / 60;

		String address = "https://app.splatoon2.nintendo.net/api/results";

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

		return sendRequestAndParseGzippedJson(request, SplatoonMatchResultsCollection.class);
	}

	private <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
		try {
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

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

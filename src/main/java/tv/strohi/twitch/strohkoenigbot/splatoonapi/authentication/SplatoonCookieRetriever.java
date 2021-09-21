package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class SplatoonCookieRetriever extends AuthenticatorBase {
	public String getSplatoonCookie(String splatoonAccessToken) {
		String address = "https://app.splatoon2.nintendo.net/?lang=en-US";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("X-IsAppAnalyticsOptedIn", "false")
				.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
				.setHeader("Accept-Encoding", "gzip,deflate")
				.setHeader("X-GameWebToken", splatoonAccessToken)
				.setHeader("Accept-Language", "en-US")
				.setHeader("X-IsAnalyticsOptedIn", "false")
				.setHeader("DNT", "0")
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.2; Pixel Build/NJH47D; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/59.0.3071.125 Mobile Safari/537.36")
				.setHeader("X-Requested-With", "com.nintendo.znca")
				.build();

		return sendRequestAndGetIksmSessionCookie(request);
	}

	private String sendRequestAndGetIksmSessionCookie(HttpRequest request) {
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response);

			if (response.statusCode() < 300) {
				List<String> foundHeaders = response.headers().allValues("Set-Cookie");
				foundHeaders.forEach(System.out::println);
				return foundHeaders.stream().filter(h -> h.contains("iksm_session")).findFirst().orElse("");
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}
}

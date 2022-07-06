package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;

import java.net.URI;
import java.net.http.HttpRequest;

public class FTokenRetriever extends AuthenticatorBase {
	private final String USER_AGENT = "stroh/1.0.0";

	public FParamLoginResult getFToken(String accessToken, String guid, int now, String iid) {
		String address = "https://flapg.com/ika2/api/login?public";

		URI uri = URI.create(address);
		String hash = getS2SApiHash(accessToken, String.format("%d", now));

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("x-token", accessToken)
				.setHeader("x-time", String.format("%d", now))
				.setHeader("x-guid", guid)
				.setHeader("x-hash", hash)
				.setHeader("x-ver", "3")
				.setHeader("x-iid", iid)
				.build();

		return sendRequestAndParseGzippedJson(request, FParamLoginResult.class);
	}

	private String getS2SApiHash(String accessToken, String timestamp) {
		String address = "https://elifessler.com/s2s/api/gen2";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(String.format("naIdToken=%s&timestamp=%s", accessToken, timestamp)))
				.uri(uri)
				.setHeader("User-Agent", USER_AGENT)
				.build();

		NaIdTokenResponse response = sendRequestAndParseGzippedJson(request, NaIdTokenResponse.class);
		return response != null ? response.getHash() : "";
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class NaIdTokenResponse {
		private String hash;
	}
}

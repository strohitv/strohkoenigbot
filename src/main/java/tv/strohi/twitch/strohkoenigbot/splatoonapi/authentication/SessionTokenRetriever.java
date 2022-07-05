package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.http.HttpRequest;

public class SessionTokenRetriever extends AuthenticatorBase {
	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		String address = accountsHost + "/connect/1.0.0/api/session_token";
		String body = String.format("client_id=%s&session_token_code=%s&session_token_code_verifier=%s", clientId, sessionTokenCode, sessionTokenCodeVerifier);

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoAppVersion + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Content-Type", "application/x-www-form-urlencoded")
				.setHeader("Accept", "application/json")
				.setHeader("Accept-Encoding", "gzip,deflate,br")
				.build();

		SessionTokenResponse response = sendRequestAndParseGzippedJson(request, SessionTokenResponse.class);
		return response != null ? response.session_token : null;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class SessionTokenResponse {
		private String code;
		private String session_token;
	}
}

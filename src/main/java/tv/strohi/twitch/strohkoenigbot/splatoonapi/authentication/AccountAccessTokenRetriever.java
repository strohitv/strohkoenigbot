package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.http.HttpRequest;

public class AccountAccessTokenRetriever extends AuthenticatorBase {
	public String getAccountAccessToken(String sessionToken) {
		String address = accountsHost + "/connect/1.0.0/api/token";
		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new GetTokenBody(sessionToken));
		} catch (JsonProcessingException e) {
			// will never happen
			e.printStackTrace();
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoAppVersion + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Accept", "application/json")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		AccessTokenResponse response = sendRequestAndParseGzippedJson(request, AccessTokenResponse.class);
		return response != null ? response.access_token : null;
	}

	@Data
	private static class GetTokenBody {
		private final String client_id = "71b963c1b7b6d119";
		private final String session_token;
		private final String grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token";
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class AccessTokenResponse {
		private String token_type;
		private int expires_in;
		private String access_token;
		private String id_token;
		private String[] scope;
	}
}

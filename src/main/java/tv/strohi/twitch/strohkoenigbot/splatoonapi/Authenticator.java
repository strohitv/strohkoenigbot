package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Authenticator {
	private final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();
	private final String host = "https://accounts.nintendo.com";

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		String address = host + "/connect/1.0.0/api/session_token";
		String body = String.format("client_id=%s&session_token_code=%s&session_token_code_verifier=%s", clientId, sessionTokenCode, sessionTokenCodeVerifier);

		URI uri = null;
		try {
			uri = new URI(address);
		} catch (URISyntaxException e) {
			// this will never happen
		}

		String nsoapp_version = "1.12.0";

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoapp_version + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Content-Type", "application/x-www-form-urlencoded")
				.setHeader("Accept", "application/json")
				.setHeader("Connection", "Keep-Alive")
				.setHeader("Accept-Encoding", "gzip,deflate,br")
				.build();

		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response);

			ObjectMapper mapper = new ObjectMapper();
			SessionTokenResponse session = mapper.readValue(response.body(), SessionTokenResponse.class);
			System.out.println(session);

			return session.session_token;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}

	private static class SessionTokenResponse {
		private String code;
		private String session_token;

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getSession_token() {
			return session_token;
		}

		public void setSession_token(String session_token) {
			this.session_token = session_token;
		}
	}
}

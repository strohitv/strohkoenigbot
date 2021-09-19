package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.UserInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.UUID;

public class Authenticator {
	private final String nsoapp_version = "1.12.0";

	private final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();
	private final String accountsHost = "https://accounts.nintendo.com";

	private final ObjectMapper mapper = new ObjectMapper();

	public String getSessionToken(String clientId, String sessionTokenCode, String sessionTokenCodeVerifier) {
		String address = accountsHost + "/connect/1.0.0/api/session_token";
		String body = String.format("client_id=%s&session_token_code=%s&session_token_code_verifier=%s", clientId, sessionTokenCode, sessionTokenCodeVerifier);

		URI uri = null;
		try {
			uri = new URI(address);
		} catch (URISyntaxException e) {
			// this will never happen
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoapp_version + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Content-Type", "application/x-www-form-urlencoded")
				.setHeader("Accept", "application/json")
				.setHeader("Accept-Encoding", "gzip,deflate,br")
				.build();

		SessionTokenResponse response = sendRequestAndParseJson(request, SessionTokenResponse.class);
		return response != null ? response.session_token : null;
	}

	public String getCookie(String sessionToken) {
		String address = accountsHost + "/connect/1.0.0/api/token";

		String body = "";
		URI uri = null;
		try {
			body = mapper.writeValueAsString(new GetTokenBody(sessionToken));
			uri = new URI(address);
		} catch (JsonProcessingException | URISyntaxException e) {
			// will never happen
			e.printStackTrace();
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoapp_version + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Accept", "application/json")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		AccessTokenResponse response = sendRequestAndParseJson(request, AccessTokenResponse.class);
		return response != null ? response.access_token : null;
	}

	public UserInfo getUserInfo(String accessToken) {
		String address = "https://api.accounts.nintendo.com/2.0.0/users/me";

		URI uri = null;
		try {
			uri = new URI(address);
		} catch (URISyntaxException e) {
			// will never happen
		}

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoapp_version + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Accept", "application/json")
				.setHeader("Authorization",  String.format("Bearer %s", accessToken))
				.setHeader("Accept-Encoding", "gzip,deflate,br")
				.build();

		return sendRequestAndParseJson(request, UserInfo.class);
	}

	public void getFToken(String accessToken) {
		String guid = UUID.randomUUID().toString();
		String now = new Date().toString();

		String address = "https://api.accounts.nintendo.com/2.0.0/users/me";

		URI uri = null;
		try {
			uri = new URI(address);
		} catch (URISyntaxException e) {
			// will never happen
		}

		String hash = getS2SApiHash(accessToken, now);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("x-token", accessToken)
				.setHeader("x-time", now)
				.setHeader("x-guid", guid)
				.setHeader("x-hash",  hash)
				.setHeader("x-ver", "3")
				.setHeader("x-iid", "nso")
				.build();

//		return sendRequestAndParseJson(request, UserInfo.class);
	}

	private String getS2SApiHash(String accessToken, String timestamp) {
		String address = "https://elifessler.com/s2s/api/gen2";

		URI uri = null;
		try {
			uri = new URI(address);
		} catch (URISyntaxException e) {
			// will never happen
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(String.format("{\"naIdToken\":\"%s\",\"timestamp\":\"%s\"", accessToken, timestamp)))
				.uri(uri)
				.setHeader("User-Agent", "splatnet2statink/1.5.12")
				.setHeader("Accept", "application/json; charset=utf-8")
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.build();

		return sendRequestAndParseJson(request, String.class);
	}

	private <T> T sendRequestAndParseJson(HttpRequest request, Class<T> valueType) {
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response);
			System.out.println(response.body());

			if (response.statusCode() < 300) {
				return mapper.readValue(response.body(), valueType);
			}
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

	private static class GetTokenBody {
		private final String client_id = "71b963c1b7b6d119";
		private final String session_token;
		private final String grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token";

		public GetTokenBody(String session_token) {
			this.session_token = session_token;
		}

		public String getClient_id() {
			return client_id;
		}

		public String getSession_token() {
			return session_token;
		}

		public String getGrant_type() {
			return grant_type;
		}
	}

	private static class AccessTokenResponse {
		private String token_type;
		private int expires_in;
		private String access_token;
		private String id_token;
		private String[] scope;

		public String getToken_type() {
			return token_type;
		}

		public void setToken_type(String token_type) {
			this.token_type = token_type;
		}

		public int getExpires_in() {
			return expires_in;
		}

		public void setExpires_in(int expires_in) {
			this.expires_in = expires_in;
		}

		public String getAccess_token() {
			return access_token;
		}

		public void setAccess_token(String access_token) {
			this.access_token = access_token;
		}

		public String getId_token() {
			return id_token;
		}

		public void setId_token(String id_token) {
			this.id_token = id_token;
		}

		public String[] getScope() {
			return scope;
		}

		public void setScope(String[] scope) {
			this.scope = scope;
		}
	}
}

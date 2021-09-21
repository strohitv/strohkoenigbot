package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.NsoAppLoginData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.UserInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.zip.GZIPInputStream;

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

		URI uri = URI.create(address);

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

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoapp_version + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Accept", "application/json")
				.setHeader("Authorization", String.format("Bearer %s", accessToken))
				.setHeader("Accept-Encoding", "gzip")
				.build();

		return sendRequestAndParseGzippedJson(request, UserInfo.class);
	}

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

		return sendRequestAndParseJson(request, FParamLoginResult.class);
	}

	public String doSplatoonAppLogin(UserInfo userInfo, FParamLoginResult fParamLoginResult) {
		String address = "https://api-lp1.znc.srv.nintendo.net/v1/Account/Login";

		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new AccountLoginBody(new LoginParameter(
					fParamLoginResult.getResult().getF(),
					fParamLoginResult.getResult().getP1(),
					fParamLoginResult.getResult().getP2(),
					fParamLoginResult.getResult().getP3(),
					userInfo.getCountry(),
					userInfo.getBirthday(),
					userInfo.getLanguage()
			)));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("Accept-Language", "en-US")
				.setHeader("User-Agent", "com.nintendo.znca/" + nsoapp_version + " (Android/7.1.2)")
				.setHeader("Accept", "application/json")
				.setHeader("X-ProductVersion", nsoapp_version)
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Authorization", "Bearer")
				.setHeader("X-Platform", "Android")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		NsoAppLoginData result = sendRequestAndParseGzippedJson(request, NsoAppLoginData.class);
		return result != null ? result.getResult().getWebApiServerCredential().getAccessToken() : "";
	}

	public String getSplatoonAccessToken(String accessToken, FParamLoginResult FParamLoginResult) {
		String address = "https://api-lp1.znc.srv.nintendo.net/v2/Game/GetWebServiceToken";

		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new SplatoonTokenRequestBody(new SplatoonTokenRequestBody.LoginParameter(
					5741031244955648L,
					FParamLoginResult.getResult().getF(),
					FParamLoginResult.getResult().getP1(),
					FParamLoginResult.getResult().getP2(),
					FParamLoginResult.getResult().getP3()
			)));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "com.nintendo.znca/" + nsoapp_version + " (Android/7.1.2)")
				.setHeader("Accept", "application/json")
				.setHeader("X-ProductVersion", nsoapp_version)
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Authorization", String.format("Bearer %s", accessToken))
				.setHeader("X-Platform", "Android")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		NsoAppLoginData result = sendRequestAndParseGzippedJson(request, NsoAppLoginData.class);
		return result != null ? result.getResult().getAccessToken() : "";
	}

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

		return sendRequestAndGetCookie(request, "iksm_session");
	}

	private String sendRequestAndGetCookie(HttpRequest request, String cookieName) {
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response);

			if (response.statusCode() < 300) {
				List<String> foundHeaders = response.headers().allValues("Set-Cookie");
				foundHeaders.forEach(System.out::println);
				return foundHeaders.stream().findFirst().orElse("");
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}

	private String getS2SApiHash(String accessToken, String timestamp) {
		String address = "https://elifessler.com/s2s/api/gen2";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(String.format("naIdToken=%s&timestamp=%s", accessToken, timestamp)))
				.uri(uri)
				.setHeader("User-Agent", "splatnet2statink/1.5.12")
				.build();

		NaIdTokenResponse response = sendRequestAndParseJson(request, NaIdTokenResponse.class);
		return response != null ? response.getHash() : "";
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

	private <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
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

	private static class NaIdTokenRequest {
		private String naIdToken;
		private String timestamp;

		public NaIdTokenRequest(String naIdToken, String timestamp) {
			this.naIdToken = naIdToken;
			this.timestamp = timestamp;
		}

		public String getNaIdToken() {
			return naIdToken;
		}

		public void setNaIdToken(String naIdToken) {
			this.naIdToken = naIdToken;
		}

		public String getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(String timestamp) {
			this.timestamp = timestamp;
		}
	}

	private static class NaIdTokenResponse {
		private String hash;

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}
	}

	private static class AccountLoginBody {
		private LoginParameter parameter;

		public AccountLoginBody() {
		}

		public AccountLoginBody(LoginParameter parameter) {
			this.parameter = parameter;
		}

		public LoginParameter getParameter() {
			return parameter;
		}

		public void setParameter(LoginParameter parameter) {
			this.parameter = parameter;
		}
	}

	private static class LoginParameter {
		String f;
		String naIdToken;
		String timestamp;
		String requestId;
		String naCountry;
		String naBirthday;
		String language;

		public LoginParameter() {
		}

		public LoginParameter(String f, String naIdToken, String timestamp, String requestId, String naCountry, String naBirthday, String language) {
			this.f = f;
			this.naIdToken = naIdToken;
			this.timestamp = timestamp;
			this.requestId = requestId;
			this.naCountry = naCountry;
			this.naBirthday = naBirthday;
			this.language = language;
		}

		public String getF() {
			return f;
		}

		public void setF(String f) {
			this.f = f;
		}

		public String getNaIdToken() {
			return naIdToken;
		}

		public void setNaIdToken(String naIdToken) {
			this.naIdToken = naIdToken;
		}

		public String getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(String timestamp) {
			this.timestamp = timestamp;
		}

		public String getRequestId() {
			return requestId;
		}

		public void setRequestId(String requestId) {
			this.requestId = requestId;
		}

		public String getNaCountry() {
			return naCountry;
		}

		public void setNaCountry(String naCountry) {
			this.naCountry = naCountry;
		}

		public String getNaBirthday() {
			return naBirthday;
		}

		public void setNaBirthday(String naBirthday) {
			this.naBirthday = naBirthday;
		}

		public String getLanguage() {
			return language;
		}

		public void setLanguage(String language) {
			this.language = language;
		}
	}

	private static class SplatoonTokenRequestBody {
		private LoginParameter parameter;

		public SplatoonTokenRequestBody() {
		}

		public SplatoonTokenRequestBody(LoginParameter parameter) {
			this.parameter = parameter;
		}

		public LoginParameter getParameter() {
			return parameter;
		}

		public void setParameter(LoginParameter parameter) {
			this.parameter = parameter;
		}

		private static class LoginParameter {
			long id;
			String f;
			String registrationToken;
			String timestamp;
			String requestId;

			public LoginParameter() {
			}

			public LoginParameter(long id, String f, String registrationToken, String timestamp, String requestId) {
				this.id = id;
				this.f = f;
				this.registrationToken = registrationToken;
				this.timestamp = timestamp;
				this.requestId = requestId;
			}

			public long getId() {
				return id;
			}

			public void setId(long id) {
				this.id = id;
			}

			public String getF() {
				return f;
			}

			public void setF(String f) {
				this.f = f;
			}

			public String getRegistrationToken() {
				return registrationToken;
			}

			public void setRegistrationToken(String registrationToken) {
				this.registrationToken = registrationToken;
			}

			public String getTimestamp() {
				return timestamp;
			}

			public void setTimestamp(String timestamp) {
				this.timestamp = timestamp;
			}

			public String getRequestId() {
				return requestId;
			}

			public void setRequestId(String requestId) {
				this.requestId = requestId;
			}
		}
	}
}

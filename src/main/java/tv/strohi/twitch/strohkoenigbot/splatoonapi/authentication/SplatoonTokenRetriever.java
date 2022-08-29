package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.NsoAppLoginData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;

public class SplatoonTokenRetriever extends AuthenticatorBase {
	public String doSplatoonAppLogin(UserInfo userInfo, FParamLoginResult fParamLoginResult, String accessToken, String guid, int now) {
		String address = "https://api-lp1.znc.srv.nintendo.net/v1/Account/Login";

		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new AccountLoginBody(new AccountLoginBody.LoginParameter(
					fParamLoginResult.getF(),
					accessToken,
					now, // String.format("%d", now), //
					guid,
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
				.setHeader("User-Agent", "com.nintendo.znca/" + nsoAppVersion + " (Android/7.1.2)")
				.setHeader("Accept", "application/json")
				.setHeader("X-ProductVersion", nsoAppVersion)
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Authorization", "Bearer")
				.setHeader("X-Platform", "Android")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		NsoAppLoginData result = sendRequestAndParseGzippedJson(request, NsoAppLoginData.class);
		return result != null ? result.getResult().getWebApiServerCredential().getAccessToken() : "";
	}

	public String getSplatoonAccessToken(String gameWebToken, FParamLoginResult FParamLoginResult, String accessToken, String guid, int now) {
		String address = "https://api-lp1.znc.srv.nintendo.net/v2/Game/GetWebServiceToken";

		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new SplatoonTokenRequestBody(new SplatoonTokenRequestBody.LoginParameter(
					5741031244955648L,
					FParamLoginResult.getF(),
					accessToken,
					Instant.ofEpochSecond(now).toString(),
					guid
			)));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", "com.nintendo.znca/" + nsoAppVersion + " (Android/7.1.2)")
				.setHeader("Accept", "application/json")
				.setHeader("X-ProductVersion", nsoAppVersion)
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.setHeader("Authorization", String.format("Bearer %s", gameWebToken))
				.setHeader("X-Platform", "Android")
				.setHeader("Accept-Encoding", "gzip")
				.build();

		NsoAppLoginData result = sendRequestAndParseGzippedJson(request, NsoAppLoginData.class);
		return result != null ? result.getResult().getAccessToken() : "";
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class AccountLoginBody {
		private LoginParameter parameter;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		private static class LoginParameter {
			private String f;
			private String naIdToken;
			private int timestamp;
			private String requestId;
			private String naCountry;
			private String naBirthday;
			private String language;
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class SplatoonTokenRequestBody {
		private LoginParameter parameter;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		private static class LoginParameter {
			long id;
			String f;
			String registrationToken;
			String timestamp;
			String requestId;
		}
	}
}

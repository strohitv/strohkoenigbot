package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.FParamLoginResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.NsoAppLoginData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.UserInfo;

import java.net.URI;
import java.net.http.HttpRequest;

public class SplatoonTokenRetriever extends AuthenticatorBase {
	public String doSplatoonAppLogin(UserInfo userInfo, FParamLoginResult fParamLoginResult) {
		String address = "https://api-lp1.znc.srv.nintendo.net/v1/Account/Login";

		URI uri = URI.create(address);

		String body = "";
		try {
			body = mapper.writeValueAsString(new AccountLoginBody(new AccountLoginBody.LoginParameter(
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

	public String getSplatoonAccessToken(String gameWebToken, FParamLoginResult FParamLoginResult) {
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
			private String timestamp;
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

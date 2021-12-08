package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.UserInfo;

import java.net.URI;
import java.net.http.HttpRequest;

public class UserInfoRetriever extends AuthenticatorBase {
	public UserInfo getUserInfo(String accountAccessToken) {
		String address = "https://api.accounts.nintendo.com/2.0.0/users/me";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("User-Agent", "OnlineLounge/" + nsoAppVersion + " NASDKAPI Android")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Accept", "application/json")
				.setHeader("Authorization", String.format("Bearer %s", accountAccessToken))
				.setHeader("Accept-Encoding", "gzip")
				.build();

		return sendRequestAndParseGzippedJson(request, UserInfo.class);
	}
}

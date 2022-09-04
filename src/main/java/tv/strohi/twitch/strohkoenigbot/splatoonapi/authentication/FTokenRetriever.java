package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.FParamLoginResult;

import java.net.URI;
import java.net.http.HttpRequest;

public class FTokenRetriever extends AuthenticatorBase {

	public FParamLoginResult getFTokenFromIminkApi(String accessToken, int method) {
		String address = "https://api.imink.app/f";

		URI uri = URI.create(address);

		String body = String.format("{\"hashMethod\":\"%d\",\"token\":\"%s\"}", method, accessToken);

		String USER_AGENT = "stroh/1.0.0";
		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("User-Agent", USER_AGENT) // "splatnet2statink/1.8.0") //
				.setHeader("Content-Type", "application/json; charset=utf-8")
				.build();

		return sendRequestAndParseGzippedJson(request, FParamLoginResult.class);
	}
}

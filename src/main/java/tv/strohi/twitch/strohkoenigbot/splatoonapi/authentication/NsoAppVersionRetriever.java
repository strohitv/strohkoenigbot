package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpRequest;

public class NsoAppVersionRetriever extends AuthenticatorBase {
	public String getNsoAppVersion() {
		String address = "https://api.imink.app/config";

		URI uri = URI.create(address);

		String USER_AGENT = "stroh/1.0.0";
		HttpRequest request = HttpRequest.newBuilder()
			.GET()
			.uri(uri)
			.setHeader("User-Agent", USER_AGENT) // "splatnet2statink/1.8.0") //
			.build();

		var response = sendRequestAndParseGzippedJson(request, NSOAppVersion.class);

		if (response != null
			&& response.getNso_version() != null
			&& response.getNso_version().matches("\\d+\\.\\d+\\.\\d+")) {
			return response.getNso_version();
		} else {
			return null;
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NSOAppVersion {
		private String nso_version;
	}
}

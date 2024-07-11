package tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.stream.Collectors;

public class DefaultUserAgentRetriever extends AuthenticatorBase {
	public String getDefaultUserAgent() {
		String address = "https://raw.githubusercontent.com/frozenpandaman/s3s/master/s3s.py";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
			.GET()
			.uri(uri)
			.build();

		var response = sendRequestAndParseGzippedJson(request, String.class);

		if (response != null) {
			var lines = response.lines().dropWhile(l -> !l.contains("Mozilla")).collect(Collectors.toList());

			var takeNextLine = true;
			var builder = new StringBuilder();
			while (takeNextLine) {
				var currentLine = lines.remove(0);
				takeNextLine = currentLine.endsWith("\\");

				builder.append(" ").append(currentLine.substring(currentLine.indexOf("'") + 1, currentLine.lastIndexOf("'") - 1).trim());
			}

			return builder.toString().trim();
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

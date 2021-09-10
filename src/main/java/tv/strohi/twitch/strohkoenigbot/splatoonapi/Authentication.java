package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class Authentication {
	private final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.build();

	Random r = new Random();

	private String generateRandom(int length) {
		byte[] bytes = new byte[length];
		r.nextBytes(bytes);
		return Base64.getUrlEncoder().encodeToString(bytes);
	}

	private String calculateChallenge(String codeVerifier) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedhash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().encodeToString(encodedhash);
		} catch (NoSuchAlgorithmException e) {
			// this will never happen.
			return "";
		}
	}

	private AuthParams generateAuthenticationParams() {
		String state = generateRandom(36);
		String codeVerifier = generateRandom(32).replace("=", "");
		String codeChallenge = calculateChallenge(codeVerifier).replace("=", "");

		return new AuthParams(state, codeVerifier, codeChallenge);
	}

	public URI buildAuthUrl() {
		AuthParams params = generateAuthenticationParams();
		try {
			return new URI(String.format("https://accounts.nintendo.com/connect/1.0.0/authorize?%s", params.getAuthStringParams()));
//			URI uri = new URI(String.format("https://accounts.nintendo.com/connect/1.0.0/authorize?%s", params.getAuthStringParams()));

//			HttpRequest request = HttpRequest.newBuilder()
//					.GET()
//					.uri(uri)
//					.setHeader("Cache-Control", "max-age=0")
//					.setHeader("Upgrade-Insecure-Requests", "1")
//					.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.2; Pixel Build/NJH47D; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/59.0.3071.125 Mobile Safari/537.36")
//					.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8n")
//					.setHeader("DNT", "1")
//					.setHeader("Accept-Encoding", "gzip,deflate,br")
//					.build();
//
//			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//			return response.uri();
		} catch (Exception e) {
			// this will never happen
			return null;
		}
	}

	private static class AuthParams {
		final String redirectUri = "npf71b963c1b7b6d119%3A%2F%2Fauth&client_id=71b963c1b7b6d119";
		final String scope = "openid+user+user.birthday+user.mii+user.screenName";
		final String responseType = "session_token_code";
		final String sessionTokenCodeChallengeMethod = "S256";
		final String theme = "login_form";

		String state;
		String codeVerifier;
		String codeChallenge;

		public AuthParams(String state, String codeVerifier, String codeChallenge) {
			this.state = state;
			this.codeVerifier = codeVerifier;
			this.codeChallenge = codeChallenge;
		}

		public String getAuthStringParams() {

			return String.format(
					"state=%s&redirect_uri=%s&scope=%s&response_type=%s&session_token_code_challenge=%s&session_token_code_challenge_method=%s&theme=%s",
					getEncoded(state),
					redirectUri,
					scope,
					responseType,
					getEncoded(codeChallenge),
					sessionTokenCodeChallengeMethod,
					theme
			);
		}

		private String getEncoded(String value) {
			try {
				value = URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
			} catch (UnsupportedEncodingException e) {
				// this will never happen
			}
			return value;
		}
	}
}

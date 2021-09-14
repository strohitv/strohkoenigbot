package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class AuthLinkCreator {
	Random random = new Random();

	private String generateRandom(int length) {
		byte[] bytes = new byte[length];
		random.nextBytes(bytes);
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

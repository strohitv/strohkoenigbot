//package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;
//
//import lombok.SneakyThrows;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.util.Base64;
//import java.util.Random;
//
//@Component
//public class LoginUrlReceiver {
//	// TODO this thing already exists.
//	//  see #AuthLinkCreator.java
//
//	@SneakyThrows
//	public TokenData generateUrl() {
//		String userAgent = "Mozilla/5.0 (Linux; Android 11; Pixel 5) " +
//				"AppleWebKit/537.36 (KHTML, like Gecko) " +
//				"Chrome/94.0.4606.61 Mobile Safari/537.36";
//
//		Random r = new Random();
//
//		byte[] authStateBytes = new byte[36];
//		r.nextBytes(authStateBytes);
//
//		byte[] authCodeVerifierBytes = new byte[32];
//		r.nextBytes(authCodeVerifierBytes);
//
//		String authState = Base64.getEncoder().encodeToString(authStateBytes);
//		String authCodeVerifier = Base64.getEncoder().encodeToString(authCodeVerifierBytes);
//
//		MessageDigest digest = MessageDigest.getInstance("SHA-256");
//		String authCodeChallenge = Base64.getEncoder()
//				.encodeToString(digest.digest(authCodeVerifier.replace("=", "").getBytes(StandardCharsets.UTF_8)))
//				.replace("=", "");
//
//		String unformattedAddress = "https://accounts.nintendo.com/connect/1.0.0/authorize?state=%s&redirect_uri=npf71b963c1b7b6d119://auth&client_id=71b963c1b7b6d119&scope=openid+user+user.birthday+user.mii+user.screenName&response_type=session_token_code&session_token_code_challenge=%s&session_token_code_challenge_method=S256&theme=login_form";
//		String address = String.format(unformattedAddress, authState, authCodeChallenge);
//
//		URI uri = URI.create(address);
//
//		HttpRequest request = HttpRequest.newBuilder()
//				.GET()
//				.uri(uri)
//				.setHeader("Cache-Control", "max-age=0")
//				.setHeader("Upgrade-Insecure-Requests", "1")
//				.setHeader("User-Agent", userAgent)
//				.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8n")
//				.setHeader("DNT", "1")
//				.setHeader("Accept-Encoding", "gzip,deflate,br")
//				.build();
//
//		try {
//			HttpClient client = HttpClient.newBuilder()
//					.version(HttpClient.Version.HTTP_2)
//					.followRedirects(HttpClient.Redirect.NORMAL)
//					.build();
//
//
//			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
//
//			while (response.previousResponse().isPresent()) {
//				response = response.previousResponse().get();
//			}
//
//			return TokenData.builder()
//					.url(response.uri().toString())
//					.authCodeVerifier(authCodeVerifier)
//					.build();
//		} catch (IOException | InterruptedException e) {
//			e.printStackTrace();
//		}
//
//		return TokenData.builder()
//				.url(address)
//				.authCodeVerifier(authCodeVerifier)
//				.build();
//	}
//}

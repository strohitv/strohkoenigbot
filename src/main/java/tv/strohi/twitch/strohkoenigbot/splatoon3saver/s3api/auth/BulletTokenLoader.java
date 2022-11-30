package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3CookieHandler;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.model.UserInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

public class BulletTokenLoader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final S3RequestSender s3RequestSender = new S3RequestSender();

	public String getBulletToken(String gToken, UserInfo userInfo) {
		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(new S3CookieHandler(gToken, true))
				.build();

		String address = "https://api.lp1.av5ja.srv.nintendo.net/api/bullet_tokens";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.noBody())
				.uri(uri)
				.setHeader("Content-Type", "application/json")
				.setHeader("Accept-Language", "en-US")
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
				.setHeader("X-Web-View-Ver", "1.0.0-d3a90678")
				.setHeader("X-NACOUNTRY", userInfo.getCountry())
				.setHeader("Accept", "*/*")
				.setHeader("Origin", "https://api.lp1.av5ja.srv.nintendo.net")
				.setHeader("X-Requested-With", "com.nintendo.znca")
				.build();

		String bulletToken;

		try {
			String result = s3RequestSender.sendRequestAndParseGzippedJson(client, request);
			logger.info(result);
			BulletToken token = new ObjectMapper().readValue(result, BulletToken.class);

			bulletToken = token.getBulletToken();
		} catch (JsonProcessingException e) {
			bulletToken = "";
		}

		return bulletToken;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	private static class BulletToken {
		@JsonProperty(value = "bulletToken")
		private String bulletToken;

		@JsonProperty(value = "lang")
		private String lang;

		@JsonProperty(value = "is_noe_country")
		private boolean is_noe_country;
	}
}

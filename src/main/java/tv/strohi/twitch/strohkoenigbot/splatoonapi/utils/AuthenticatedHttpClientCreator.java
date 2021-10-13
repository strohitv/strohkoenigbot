package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

@Component
public class AuthenticatedHttpClientCreator {
	private SplatoonCookieHandler splatoonCookieHandler;

	@Autowired
	public void setSplatoonCookieHandler(SplatoonCookieHandler splatoonCookieHandler) {
		this.splatoonCookieHandler = splatoonCookieHandler;
	}

	@Bean
	public HttpClient getAuthenticatedHttpClient() {
		return HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(splatoonCookieHandler)
				.build();
	}
}
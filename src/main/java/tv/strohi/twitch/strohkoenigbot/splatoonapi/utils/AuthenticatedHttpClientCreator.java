package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.LogQueuer;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.LogSender;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthenticatedHttpClientCreator {
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final LogQueuer logQueuer;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	public HttpClient createFor(Account account) {
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(120))
			.version(HttpClient.Version.HTTP_2)
			.cookieHandler(SplatoonCookieHandler.of(account, accountRepository, configurationRepository, logQueuer, logSender, exceptionLogger))
			.build();
	}
}

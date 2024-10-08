package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class AuthenticatedHttpClientCreator {
	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public HttpClient createFor(Account account) {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(120))
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(SplatoonCookieHandler.of(account, accountRepository, discordBot))
				.build();
	}
}

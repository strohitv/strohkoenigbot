package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;

import java.net.http.HttpClient;

@Component
public class AuthenticatedHttpClientCreator {
	private DiscordAccountRepository discordAccountRepository;

	@Autowired
	public void setDiscordAccountRepository(DiscordAccountRepository discordAccountRepository) {
		this.discordAccountRepository = discordAccountRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public HttpClient of(Account account) {
		return HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(SplatoonCookieHandler.of(account, discordAccountRepository, configurationRepository, discordBot))
				.build();
	}
}

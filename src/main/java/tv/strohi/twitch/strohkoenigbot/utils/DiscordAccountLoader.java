package tv.strohi.twitch.strohkoenigbot.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;

@Component
public class DiscordAccountLoader {
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

	public Account loadAccount(long discordId) {
		Account account = accountRepository.findByDiscordIdOrderById(discordId).stream().findFirst().orElse(null);

		if (account == null) {
			account = Account.builder()
					.discordId(discordId)
					.id(0L)
					.rateLimitNumber(0)
					.enableSplatoon3(false)
					.build();

			account = accountRepository.save(account);

			discordBot.sendPrivateMessage(discordId, "Hi! Nice to meet you!");
		}

		return account;
	}
}

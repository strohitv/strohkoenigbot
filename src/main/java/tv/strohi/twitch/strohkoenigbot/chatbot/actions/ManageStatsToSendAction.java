package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;

import java.util.EnumSet;

@Component
public class ManageStatsToSendAction extends ChatAction {

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private DiscordAccountLoader discordAccountLoader;

	@Autowired
	public void setDiscordAccountLoader(DiscordAccountLoader discordAccountLoader) {
		this.discordAccountLoader = discordAccountLoader;
	}

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!stats")) {
			message = message.substring("!stats".length()).trim();

			if (message.startsWith("weapons on") || message.startsWith("weapons off")) {
				Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

				if (account.getSplatoonCookie() != null && !account.getSplatoonCookie().isBlank()) {
					if (account.getTimezone() != null && !account.getTimezone().isBlank()) {
						account.setShouldSendDailyStats(message.startsWith("weapons on"));
						accountRepository.save(account);

						if (account.getShouldSendDailyStats()) {
							sender.send("Alright, you'll receive weapon stats every day at roughly 0:15 am!");
						} else {
							sender.send("Alright, I won't send you weapon stats once every day (anymore)!");
						}
					} else {
						sender.send("**ERROR** Please set your timezone by using `!timezone find` first!");
					}
				} else {
					sender.send("**ERROR** Please set your splatoon cookie by using `!splatoon2 register` first!");
				}
			} else {
				sender.send("Allowed commands:\n    - !stats weapons on\n    - !stats weapons off");
			}
		}
	}
}

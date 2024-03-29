package tv.strohi.twitch.strohkoenigbot.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserCounter {
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

	private SchedulingService schedulingService;

	@Autowired
	public void setSchedulingService(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("UserCounter_schedule", CronSchedule.getScheduleString("10 5 0 * * *"), this::sendUserNumbers);
	}

//	@Scheduled(cron = "10 5 0 * * *")
	public void sendUserNumbers() {
		List<Account> allUsers = accountRepository.findAll().stream()
				.sorted(Comparator.comparingLong(Account::getId))
				.collect(Collectors.toList());

		Long mainAccountId = allUsers.stream()
				.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
				.map(Account::getDiscordId)
				.findFirst()
				.orElse(null);

		if (mainAccountId != null) {
			StringBuilder builder = new StringBuilder("This bot currently has **").append(allUsers.size()).append("** users\n\nList of all users:");

			for (Account account : allUsers) {
				builder.append("\n- id: **").append(account.getId()).append("** - name: **").append(discordBot.loadUserNameFromServer(account.getDiscordId())).append("**");
			}

			discordBot.sendPrivateMessage(mainAccountId, builder.toString());
		}
	}
}

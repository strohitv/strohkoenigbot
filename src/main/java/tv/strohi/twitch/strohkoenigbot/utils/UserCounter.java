package tv.strohi.twitch.strohkoenigbot.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.LogQueuer;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class UserCounter implements ScheduledService {
	private final AccountRepository accountRepository;
	private final DiscordBot discordBot;
	private final LogQueuer logQueuer;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("UserCounter_schedule")
			.schedule(CronSchedule.getScheduleString("10 5 0 * * *"))
			.runnable(this::sendUserNumbers)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	//	@Scheduled(cron = "10 5 0 * * *")
	public void sendUserNumbers() {
		var allUsers = accountRepository.findAll().stream()
			.sorted(Comparator.comparingLong(Account::getId))
			.collect(Collectors.toList());

		var mainAccountId = allUsers.stream()
			.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
			.map(Account::getDiscordId)
			.findFirst()
			.orElse(null);

		if (mainAccountId != null) {
			var builder = new StringBuilder("This bot currently has **").append(allUsers.size()).append("** users\n\nList of all users:");

			for (var account : allUsers) {
				discordBot.searchUsername(account.getDiscordId()).ifPresentOrElse(
					name ->
						builder.append("\n- id: **").append(account.getId()).append("** - name: **").append(discordBot.searchUsername(account.getDiscordId())).append("**"),
					() -> {
						logQueuer.infoQueue(log, "Discord account with id `%d` does not exist anymore and will be removed from the database!", account.getId());
						accountRepository.delete(account);
					});
			}

			discordBot.sendPrivateMessage(mainAccountId, builder.toString());
		}
	}
}

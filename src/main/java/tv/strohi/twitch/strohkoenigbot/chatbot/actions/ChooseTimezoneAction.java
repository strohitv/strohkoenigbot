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

import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ChooseTimezoneAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private final List<String> availableZoneIds;

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

	public ChooseTimezoneAction() {
		Set<String> zids = ZoneId.getAvailableZoneIds();
		availableZoneIds = zids.stream().sorted().collect(Collectors.toList());
	}

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!timezone find") || message.startsWith("!timezone set")) {
			message = message.substring("!timezone".length()).trim();

			if (message.startsWith("find")) {
				message = message.substring("find".length()).trim();

				String[] timeGroups = extractTimeGroups(message);
				if (timeGroups.length > 0) {
					int hour = Integer.parseInt(timeGroups[0].split(":")[0]);

					List<String> possibleZids = availableZoneIds.stream().filter(z -> Instant.now().atZone(ZoneId.of(z)).getHour() == hour).sorted().collect(Collectors.toList());
					List<Integer> zidsPositions = possibleZids.stream().map(availableZoneIds::indexOf).collect(Collectors.toList());

					StringBuilder builder = new StringBuilder("Available timezones for the selected time:\n\n");
					for (int i = 0; i < possibleZids.size(); i++) {
						builder.append(zidsPositions.get(i)).append(": ").append(possibleZids.get(i)).append("\n");
					}

					builder.append("\nPlease set your timezone using **!timezone set <number>**");

					sender.send(builder.toString());
				} else {
					sender.send("No Time found! Usage:\n" +
							"- first use **!timezone find <your time>** to list all available timezones. The time has to be in 24h format, no am or pm allowed. Example: **!timezone find 19:34** if it's 19:34 (7:34 pm) at your place.");
				}
			} else {
				String selection = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, "")).substring("!timezone set".length()).trim();

				if (availableZoneIds.contains(selection)) {
					Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));
					account.setTimezone(selection);
					accountRepository.save(account);

					sender.send(String.format("Your timezone has been set to **%s**!", selection));
				} else {
					try {
						int number = Integer.parseInt(selection);

						if (number >= 0 && number < availableZoneIds.size()) {
							String zone = availableZoneIds.get(number);

							Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));
							account.setTimezone(zone);
							accountRepository.save(account);

							sender.send(String.format("Your timezone has been set to **%s**!", zone));
						} else {
							sender.send(String.format("Please select a timezone from the list! Use **!timezone set <number>** to set it to the timezone you're in. Example: **!timezone set %d** if your timezone is in 'Europe/London'", availableZoneIds.indexOf("Europe/London")));
						}
					} catch (NumberFormatException ignored) {
						sender.send(String.format("Please use **!timezone set <number>** to set it to the timezone you're in. Example: **!timezone set %d** if your timezone is in 'Europe/London'", availableZoneIds.indexOf("Europe/London")));
					}
				}
			}
		} else if (message.startsWith("!timezone")) {
			sender.send(String.format("Do this to set your timezone:\n" +
					"- first use **!timezone find <your time>** to list all available timezones. The time has to be in 24h format, no am or pm allowed. Example: **!timezone find 19:34** if it's 19:34 (7:34 pm) at your place.\n" +
					"- afterwards use **!timezone set <number>** to set it to the timezone you're in. Example: **!timezone set %d** if your timezone is in 'Europe/London'", availableZoneIds.indexOf("Europe/London")));
		}
	}

	private String[] extractTimeGroups(String message) {
		return Pattern.compile("([0-2][0-3]|[0-1]?\\d):[0-5]\\d")
				.matcher(message)
				.results()
				.map(mr -> mr.group(1))
				.toArray(String[]::new);
	}
}

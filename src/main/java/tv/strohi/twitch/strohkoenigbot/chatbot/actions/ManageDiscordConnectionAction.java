package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.AbilityNotification;
import tv.strohi.twitch.strohkoenigbot.data.model.DiscordAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@Component
public class ManageDiscordConnectionAction extends ChatAction {
	private final DiscordAccountRepository discordAccountRepository;
	private final AbilityNotificationRepository abilityNotificationRepository;

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;

		this.discordBot.subscribe(id -> {
			DiscordAccount account = discordAccountRepository.findByDiscordId(id).stream().findFirst().orElse(null);

			if (account != null) {
				account.setConsent(true);
				discordAccountRepository.save(account);

				discordBot.sendPrivateMessage(id, "This discord account is now connected.");
			} else {
				discordBot.sendPrivateMessage(id, "ERROR: Could not find you in database. Please restart the connection attempt.");
			}
		});
	}

	private TwitchMessageSender messageSender;

	@Autowired
	public void setMessageSender(TwitchMessageSender messageSender) {
		this.messageSender = messageSender;
	}

	@Autowired
	public ManageDiscordConnectionAction(DiscordAccountRepository discordAccountRepository, AbilityNotificationRepository abilityNotificationRepository) {
		this.discordAccountRepository = discordAccountRepository;
		this.abilityNotificationRepository = abilityNotificationRepository;
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage);
	}

	@Override
	public void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		boolean remove = false;
		if (message == null) {
			return;
		}

		message = message.trim();

		if (!message.toLowerCase().startsWith("!connect") && !(remove = message.toLowerCase().startsWith("!disconnect"))) {
			return;
		}

		if (remove) {
			List<AbilityNotification> notifications = abilityNotificationRepository.findByUserId((String) args.getArguments().get(ArgumentKey.ChannelId));
			if (notifications.size() > 0) {
				abilityNotificationRepository.deleteAll(notifications);
			}

			DiscordAccount account = discordAccountRepository.findByTwitchUserId((String) args.getArguments().get(ArgumentKey.ChannelId)).stream()
					.findFirst()
					.orElse(null);

			if (account != null) {
				discordAccountRepository.delete(account);

				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"Your connection to a discord account got removed. This also means all your notifications were removed.",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			} else {
				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"ERROR: You weren't connected to any discord account => there was no need to disconnect you.",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			}
		} else {
			message = message.substring("!connect".length()).trim();

			String discordTag = Arrays.stream(message.split(" "))
					.filter(m -> m.contains("#"))
					.findFirst()
					.orElse(null);

			if (discordTag != null) {
				Long id = discordBot.loadUserIdFromDiscordServer(discordTag);

				if (id != null) {
					if (discordAccountRepository.findByDiscordId(id).size() == 0) {
						DiscordAccount account = new DiscordAccount();
						account.setConsent(false);
						account.setDiscordId(id);
						account.setTwitchUserId((String) args.getArguments().get(ArgumentKey.ChannelId));

						account = discordAccountRepository.save(account);

						if (discordBot.sendPrivateMessage(account.getDiscordId(),
								String.format("Hello!\nTwitch user '@%s' wants to connect his account with your discord account to receive automated notifications about new gear in the splat net shop.\n\nIf this is you and you want to receive those notifications, please respond with 'yes'. If this is not you or you don't want to receive any more messages, please ignore this message.",
										args.getArguments().get(ArgumentKey.ChannelName)))) {
							messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
									"I sent you a message on discord. Please respond with 'yes' to it to finish the connection process.",
									(String) args.getArguments().get(ArgumentKey.MessageNonce),
									(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
						}
					} else {
						messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
								"ERROR: It seems like there is already a connection attempt in progress. Please finish that one first or use '!disconnect' to cancel it -> I can't connect you.",
								(String) args.getArguments().get(ArgumentKey.MessageNonce),
								(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
					}
				} else {
					messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
							"ERROR: It seems like you are not on my discord server. Please join first using this link: https://discord.gg/rSHw2gNjDA -> I can't connect you.",
							(String) args.getArguments().get(ArgumentKey.MessageNonce),
							(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
				}
			} else {
				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"ERROR: I couldn't find any discord user id in your message -> I can't connect you.",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			}
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ConnectionAccepted;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.AbilityNotification;
import tv.strohi.twitch.strohkoenigbot.data.model.DiscordAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static tv.strohi.twitch.strohkoenigbot.utils.ParseUtils.parseLongSafe;

@Component
public class ManageDiscordConnectionAction extends ChatAction {
	private final DiscordAccountRepository discordAccountRepository;
	private final AbilityNotificationRepository abilityNotificationRepository;

	private DiscordBot discordBot;

	private final ConnectionAccepted accepted;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;

		this.discordBot.subscribe(accepted);
	}

	@Autowired
	public ManageDiscordConnectionAction(DiscordAccountRepository discordAccountRepository, AbilityNotificationRepository abilityNotificationRepository) {
		this.discordAccountRepository = discordAccountRepository;
		this.abilityNotificationRepository = abilityNotificationRepository;

		accepted = discordId -> {
			DiscordAccount account = discordAccountRepository.findByDiscordIdOrderById(discordId).stream().findFirst().orElse(null);

			if (account != null) {
				account.setConsent(true);
				discordAccountRepository.save(account);

				discordBot.sendPrivateMessage(discordId, "This discord account is now connected.");
			} else {
				discordBot.sendPrivateMessage(discordId, "ERROR: Could not find you in database. Please restart the connection attempt.");
			}
		};
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	public void execute(ActionArgs args) {
		boolean isTwitchMessage = args.getReason() == TriggerReason.ChatMessage || args.getReason() == TriggerReason.PrivateMessage;
		TwitchDiscordMessageSender sender = args.getReplySender();

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
			DiscordAccount account = discordAccountRepository.findByDiscordIdOrTwitchUserIdOrderById
					(
							parseLongSafe(args.getUserId()),
							(isTwitchMessage) ? (String) args.getArguments().get(ArgumentKey.ChannelName) : null
					)
					.stream()
					.findFirst()
					.orElse(null);

			if (account != null) {
				List<AbilityNotification> notifications = abilityNotificationRepository.findByDiscordIdOrderById(account.getId());
				if (notifications.size() > 0) {
					abilityNotificationRepository.deleteAll(notifications);
				}

				discordAccountRepository.delete(account);

				sender.send("Your discord account + twitch connection got removed from my bot. This also means all your registered notifications were deleted.");
			} else {
				sender.send("ERROR: I don't know you => there was no need to remove anything.");
			}
		} else {
			message = message.substring("!connect".length()).trim();
			String discordTag;
			if (isTwitchMessage) {
				discordTag = Arrays.stream(message.split(" "))
						.filter(m -> m.contains("#"))
						.findFirst()
						.orElse(null);
			} else {
				MessageCreateEvent event = (MessageCreateEvent) args.getArguments().get(ArgumentKey.Event);
				discordTag = event.getMessage().getAuthor().map(User::getTag).orElse(null);
			}

			if (discordTag != null) {
				Long id = discordBot.loadUserIdFromDiscordServer(discordTag);

				if (id != null) {
					DiscordAccount account = discordAccountRepository.findByDiscordIdOrderById(id).stream()
							.findFirst()
							.orElse(null);

					if (account == null || (isTwitchMessage && account.getTwitchUserId() == null)) {
						if (account == null) {
							account = new DiscordAccount();
							account.setDiscordId(id);
						}

						if (isTwitchMessage) {
							account.setConsent(false);
							account.setTwitchUserId(args.getUserId());
						}

						account = discordAccountRepository.save(account);

						if (isTwitchMessage) {
							if (discordBot.sendPrivateMessage(account.getDiscordId(),
									String.format("Hello!\nTwitch user '@%s' wants to connect his account with your discord account to receive automated notifications about new gear in the splat net shop.\n\nIf this is you and you want to receive those notifications, please respond with 'yes'. If this is not you or you don't want to receive any more messages, please ignore this message.",
											args.getArguments().get(ArgumentKey.ChannelName)))) {
								sender.send("I sent you a message on discord. Please respond with 'yes' to it to finish the connection process.");
							}
						} else {
							accepted.accept(account.getDiscordId());
						}
					} else {
						sender.send("ERROR: It seems like either this account is already connected or there already is a connection attempt in progress. Please finish that one first or use '!disconnect' to cancel it -> I can't connect you.");
					}
				} else {
					sender.send("ERROR: It seems like you are not on my discord server. Please join first using this link: https://discord.gg/rSHw2gNjDA -> I can't connect you.");
				}
			} else {
				sender.send("ERROR: I couldn't find any discord user id in your message -> I can't connect you.");
			}
		}
	}
}

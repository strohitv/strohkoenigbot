package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;

import java.util.EnumSet;

@Component
public class DiscordAdministrationAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private TwitchAuthRepository authRepository;

	@Autowired
	public void setAuthRepository(TwitchAuthRepository authRepository) {
		this.authRepository = authRepository;
	}

	private TwitchChatBot twitchChatBot;

	@Autowired
	public void setTwitchChatBot(TwitchChatBot twitchChatBot) {
		this.twitchChatBot = twitchChatBot;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null || !"strohkoenig#8058".equals(args.getUser())) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!setbottoken")) {
			String newBotToken = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!setbottoken".length()).trim();

			TwitchAuth auth = authRepository.findByIsMain(false).stream().findFirst().orElse(null);
			if (auth != null) {
				auth.setToken(newBotToken);
				authRepository.save(auth);

				twitchChatBot.initializeClients();

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Token was set successfully.");
			}
		}
	}
}

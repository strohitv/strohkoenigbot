package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Clip;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2ClipRepository;

import java.util.EnumSet;

@Component
public class RateGamePlayAction extends ChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage);
	}

	private TwitchMessageSender messageSender;

	@Autowired
	public void setMessageSender(TwitchMessageSender messageSender) {
		this.messageSender = messageSender;
	}

	private TwitchBotClient botClient;

	@Autowired
	public void setBotClient(TwitchBotClient botClient) {
		this.botClient = botClient;
	}

	private Splatoon2ClipRepository clipRepository;

	@Autowired
	public void setClipRepository(Splatoon2ClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!rate")) {
			logger.info("Rate gameplay action was called");
			logger.info(message);
			messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
					"I want to improve my gameplay. Whenever I play well or badly, please write \"!good DESCRIPTION\" or \"!bad DESCRIPTION\" in the chat to tell me about it. For example: \"!good You saved your team mate from the flanker\" after I've done exactly that in a match. We're going to review those ratings after each match. strohk2PogFree",
					(String) args.getArguments().get(ArgumentKey.MessageNonce),
					(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
		} else if (message.startsWith("!good ") || message.startsWith("!bad ")) {
			logger.info("Rate gameplay action was called");
			logger.info(message);

			String channelId = (String) args.getArguments().getOrDefault(ArgumentKey.ChannelId, null);
			Splatoon2Clip clip = botClient.createClip(message.substring("00000".length()).trim(), channelId, message.startsWith("!good"));

			logger.info(clip);

			if (clip != null) {
				accountRepository.findByTwitchUserId(channelId).ifPresent(account -> clip.setAccountId(account.getId()));

				clipRepository.save(clip);

				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						String.format("Alright, my %s action has been noted. Thank you very much! Clip URL: %s strohk2UwuFree",
								message.startsWith("!good") ? "good" : "bad",
								clip.getClipUrl()
						),
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			} else {
				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"I could not save your rating, either because there haven't been 20 seconds passed since the last rating or the stream is not live at the moment. Please try again in some seconds. strohk2HuhFree",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			}
		}
	}
}

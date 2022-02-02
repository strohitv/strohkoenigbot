package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonClip;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonClipRepository;

import java.util.EnumSet;

@Component
public class RateGamePlayAction extends ChatAction {
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
	public void setBotClient(@Qualifier("botClient") TwitchBotClient botClient) {
		this.botClient = botClient;
	}

	private SplatoonClipRepository clipRepository;

	@Autowired
	public void setClipRepository(SplatoonClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!rate")) {
			messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
					"I want to improve my gameplay. Whenever I play well or badly, please write \"!good DESCRIPTION\" or \"!bad DESCRIPTION\" in the chat to tell me about it. For example: \"!good You saved your team mate from the flanker\" after I've done exactly that in a match. We're going to review those ratings after each match. strohk2PogFree",
					(String) args.getArguments().get(ArgumentKey.MessageNonce),
					(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
		} else if (message.startsWith("!good") || message.startsWith("!bad")) {
			SplatoonClip clip = botClient.createClip(message.substring("!rate".length()).trim(), message.startsWith("!good"));

			if (clip != null) {
				clipRepository.save(clip);

				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"Alright, it's noted. Thank you very much! strohk2UwuFree",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			} else {
				messageSender.reply((String) args.getArguments().get(ArgumentKey.ChannelName),
						"I could not save your rating, either because there haven't been 20 seconds passed since the last rating or the stream is not live at the moment. Please try again in some seconds. strohk2OhFree",
						(String) args.getArguments().get(ArgumentKey.MessageNonce),
						(String) args.getArguments().get(ArgumentKey.ReplyMessageId));
			}
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.TwitchLiveEvent;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchSoAccountRepository;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Log4j2
public class AutoSoAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.Raid, TriggerReason.LiveStatus);
	}

	private final Map<String, Boolean> accountsToShoutOut = new HashMap<>();

	private final TwitchSoAccountRepository twitchSoAccountRepository;
	private final TwitchMessageSender twitchMessageSender;

	@Override
	public void execute(ActionArgs args) {
		if (args.getReason() == TriggerReason.LiveStatus) {
			if (((TwitchLiveEvent) args.getArguments().get(ArgumentKey.Event)).isLive()) {
				startStream();
			} else {
				endStream();
			}
		} else if (args.getReason() == TriggerReason.Raid) {
			accountsToShoutOut.put(args.getUser().toLowerCase(), false);
			new Thread(() -> sendTwitchSoMessage(args.getUser(), (String) args.getArguments().get(ArgumentKey.ChannelName), 15_000)).start();
		} else if (accountsToShoutOut.getOrDefault(args.getUser().toLowerCase(), false)) {
			accountsToShoutOut.put(args.getUser().toLowerCase(), false);
			new Thread(() -> sendTwitchSoMessage(args.getUser(), (String) args.getArguments().get(ArgumentKey.ChannelName), 15_000)).start();
		}
	}

	private void sendTwitchSoMessage(String user, String channel, int waitTime) {
		if (waitTime > 0) {
			try {
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				log.error(e);
			}
		}

		twitchMessageSender.send(channel, String.format("!so %s", user));
	}

	public void startStream() {
		accountsToShoutOut.clear();
		twitchSoAccountRepository.findAll().forEach(soa -> accountsToShoutOut.put(soa.getUsername(), true));
	}

	public void endStream() {
		accountsToShoutOut.clear();
	}
}

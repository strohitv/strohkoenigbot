package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchSoAccountRepository;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Component
public class AutoSoAction implements IChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.Raid);
	}

	private final Map<String, Boolean> accountsToShoutOut = new HashMap<>();

	private TwitchSoAccountRepository twitchSoAccountRepository;

	@Autowired
	public void setTwitchSoAccountRepository(TwitchSoAccountRepository twitchSoAccountRepository) {
		this.twitchSoAccountRepository = twitchSoAccountRepository;
	}

	private TwitchMessageSender twitchMessageSender;

	@Autowired
	public void setTwitchMessageSender(TwitchMessageSender twitchMessageSender) {
		this.twitchMessageSender = twitchMessageSender;
	}

	@Override
	public void run(ActionArgs args) {
		if (accountsToShoutOut.getOrDefault(args.getUser().toLowerCase(), false)) {
			accountsToShoutOut.put(args.getUser().toLowerCase(), false);

			if (args.getReason() == TriggerReason.Raid) {
				new Thread(() -> sendTwitchSoMessage(args.getUser(), true)).start();
			} else {
				sendTwitchSoMessage(args.getUser(), false);
			}
		}
	}

	private void sendTwitchSoMessage(String user, boolean wait) {
		if (wait) {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				logger.error(e);
			}
		}

		twitchMessageSender.send("strohkoenig", String.format("!so %s", user));
	}

	public void startStream() {
		accountsToShoutOut.clear();
		twitchSoAccountRepository.findAll().forEach(soa -> accountsToShoutOut.put(soa.getUsername(), true));
	}

	public void endStream() {
		accountsToShoutOut.clear();
	}
}

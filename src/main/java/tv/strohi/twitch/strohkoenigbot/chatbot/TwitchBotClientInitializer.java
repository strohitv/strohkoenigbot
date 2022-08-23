package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.AutoSoAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import java.util.List;

@Component
public class TwitchBotClientInitializer {
	@Autowired
	public TwitchBotClientInitializer(TwitchBotClient client,
									  List<IChatAction> actions,
									  AutoSoAction autoSoAction,
									  ResultsExporter resultsExporter,
									  AccountRepository accountRepository) {
		TwitchBotClient.setBotActions(actions);

		client.setGoLiveListener(client.getClient().getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
			if (resultsExporter != null) {
				Account account = accountRepository.findByTwitchUserId(event.getChannel().getId()).orElse(null);
				resultsExporter.start(account);
			}

			autoSoAction.startStream();
		}));

		client.setGoOfflineListener(client.getClient().getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
			if (resultsExporter != null) {
				Account account = accountRepository.findByTwitchUserId(event.getChannel().getId()).orElse(null);
				resultsExporter.stop(account);
			}

			autoSoAction.endStream();
		}));
	}
}

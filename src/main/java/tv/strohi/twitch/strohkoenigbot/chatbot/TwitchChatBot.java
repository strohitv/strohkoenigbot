package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.model.TwitchAuthData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import javax.annotation.PreDestroy;
import java.util.List;

@Component
public class TwitchChatBot {
	private final TwitchAuthData authData = new TwitchAuthData();
	private TwitchClient botClient;
	private TwitchClient mainAccountClient;
	private List<IChatAction> botActions;

	private TwitchMessageSender messageSender;

	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	private ResultsExporter resultsExporter;

	@Autowired
	public void setResultsExporter(ResultsExporter resultsExporter) {
		this.resultsExporter = resultsExporter;
	}

	IDisposable goLiveListener;
	IDisposable goOfflineListener;

	@Bean("botClient")
	public TwitchClient getBotClient() {
		if (botClient == null) {
			initializeBotClient();
		}

		return botClient;
	}

	@Bean("mainAccountClient")
	public TwitchClient getMainAccountClient() {
		if (mainAccountClient == null) {
			initializeMainAccountClient();
		}

		return mainAccountClient;
	}

	public void initializeMainAccountClient() {
		if (mainAccountClient == null) {
			OAuth2Credential mainAccountCredential = new OAuth2Credential("twitch", authData.getMainAccountAuthToken());

			mainAccountClient = TwitchClientBuilder.builder()
					.withDefaultAuthToken(mainAccountCredential)
					.withEnableChat(true)
					.withChatAccount(mainAccountCredential)
					.withEnableHelix(true)
					.build();

			mainAccountClient.getChat().joinChannel(authData.getMainAccountUsername());

			if (mainAccountClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				mainAccountClient.getChat().sendMessage(authData.getMainAccountUsername(), "Hi! strohk2Pog");
			}
		}
	}

	private void initializeBotClient() {
		if (botClient == null) {
			OAuth2Credential botCredential = new OAuth2Credential("twitch", authData.getBotAuthToken());

			botClient = TwitchClientBuilder.builder()
					.withDefaultAuthToken(botCredential)
					.withEnableChat(true)
					.withChatAccount(botCredential)
					.withEnableHelix(true)
					.withEnablePubSub(true)
					.build();

			User strohkoenigUser = botClient.getClientHelper().enableStreamEventListener("strohkoenig");
			botClient.getClientHelper().enableFollowEventListener("strohkoenig");

			if (strohkoenigUser != null) {
				authData.setMainAccountChannelId(strohkoenigUser.getId());
			}

			goLiveListener = botClient.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
				resultsExporter.start(event.getFiredAtInstant());
			});

			goOfflineListener = botClient.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
				resultsExporter.stop();
			});

			botClient.getChat().joinChannel(authData.getMainAccountUsername());

			if (botClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				botClient.getChat().sendMessage(authData.getMainAccountUsername(), "Hi! strohk2Pog");
			}

			botClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
				ActionArgs args = new ActionArgs();

				args.setReason(TriggerReason.ChatMessage);
				args.setUser(event.getUser().getName());
				args.setUserId(event.getUser().getId());

				args.getArguments().put(ArgumentKey.Event, event);

				args.getArguments().put(ArgumentKey.ChannelId, event.getMessageEvent().getChannelId());
				args.getArguments().put(ArgumentKey.ChannelName, event.getMessageEvent().getChannelName().orElse(null));
				args.getArguments().put(ArgumentKey.Message, event.getMessage());
				args.getArguments().put(ArgumentKey.MessageNonce, event.getNonce());
				args.getArguments().put(ArgumentKey.ReplyMessageId, event.getMessageEvent().getMessageId().orElse(event.getEventId()));

				botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
			});
		}
	}

	@PreDestroy
	public void stop() {
		if (goLiveListener != null) {
			if (!goLiveListener.isDisposed()) {
				goLiveListener.dispose();
			}

			goLiveListener = null;
		}

		if (goOfflineListener != null) {
			if (!goOfflineListener.isDisposed()) {
				goOfflineListener.dispose();
			}

			goOfflineListener = null;
		}

		if (botClient != null) {
			if (botClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				botClient.getChat().sendMessage(authData.getMainAccountUsername(), "Bye!");
				botClient.getChat().leaveChannel(authData.getMainAccountUsername());
			}

			botClient.getEventManager().getActiveSubscriptions().forEach(IDisposable::dispose);
			botClient.getEventManager().close();
			botClient.close();

			botClient = null;
		}

		if (mainAccountClient != null) {
			if (mainAccountClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				mainAccountClient.getChat().sendMessage(authData.getMainAccountUsername(), "Bye!");
				mainAccountClient.getChat().leaveChannel(authData.getMainAccountUsername());
			}

			mainAccountClient.close();

			mainAccountClient = null;
		}
	}
}

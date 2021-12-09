package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import java.util.List;

public class TwitchBotClient {
	private TwitchClient client;
	private final ResultsExporter resultsExporter;
	private final List<IChatAction> botActions;
	private final String channelName;

	public TwitchBotClient(ResultsExporter resultsExporter, List<IChatAction> botActions, String channelName) {
		this.resultsExporter = resultsExporter;
		this.botActions = botActions;
		this.channelName = channelName;
	}

	public TwitchClient getClient() {
		return client;
	}

	public void setClient(TwitchClient client) {
		this.client = client;
	}

	IDisposable goLiveListener;
	IDisposable goOfflineListener;

	public void initializeClient(TwitchAuth auth) {
		if (client == null) {
			try {
				OAuth2Credential botCredential = new OAuth2Credential("twitch", auth.getToken());

				TwitchClientBuilder builder = TwitchClientBuilder.builder()
						.withDefaultAuthToken(botCredential)
						.withEnableChat(true)
						.withChatAccount(botCredential)
						.withEnableHelix(true);

				if (!auth.getIsMain()) {
					builder = builder.withEnablePubSub(true);
				}

				client = builder.build();

				if (!auth.getIsMain()) {
//					User strohkoenigUser = client.getClientHelper().enableStreamEventListener(channelName);
					client.getClientHelper().enableStreamEventListener(channelName);
					client.getClientHelper().enableFollowEventListener(channelName);

					goLiveListener = client.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
						resultsExporter.start(event.getFiredAtInstant());
					});

					goOfflineListener = client.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
						resultsExporter.stop();
					});

					client.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
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

					client.getEventManager().onEvent(PrivateMessageEvent.class, event -> {
						ActionArgs args = new ActionArgs();

						args.setReason(TriggerReason.PrivateMessage);
						args.setUser(event.getUser().getName());
						args.setUserId(event.getUser().getId());

						args.getArguments().put(ArgumentKey.Event, event);
						args.getArguments().put(ArgumentKey.Message, event.getMessage());
						;

						botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
					});

//					UserList users = client.getHelix().getUsers(auth.getToken(), null, Collections.singletonList("strohkoenig")).execute();
//					client.getChat().sendPrivateMessage(users.getUsers().get(0).getLogin(), "test123");
				}

				client.getChat().joinChannel(channelName);
				if (client.getChat().isChannelJoined(channelName)) {
					client.getChat().sendMessage(channelName, "Hi! strohk2Pog");
//					client.getChat().sendMessage(channelName, "/w strohkoenig Hi! strohk2Pog");
//					client.getChat().sendMessage(channelName, "/w strohkoenigbot Hi! strohk2Pog");
//
//					client.getChat().sendPrivateMessage("strohkoenig", "Hi 123");
//					client.getChat().sendPrivateMessage("strohkoenigbot", "Hi 123");
				}
			} catch (Exception ignored) {

			}
		}
	}

	public void stop() {
		if (resultsExporter != null) {
			resultsExporter.stop();
		}

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

		if (client != null) {
			if (client.getChat().isChannelJoined(channelName)) {
				client.getChat().sendMessage(channelName, "Bye!");
				client.getChat().leaveChannel(channelName);
			}

			client.getEventManager().getActiveSubscriptions().forEach(IDisposable::dispose);
			client.getEventManager().close();
			client.close();

			client = null;
		}
	}
}

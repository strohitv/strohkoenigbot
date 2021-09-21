package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.model.TwitchAuthData;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.ResultsExporter;

import java.util.List;

@Component
public class TwitchChatBot {
	private final TwitchAuthData authData = new TwitchAuthData();
	private TwitchClient botClient;
	private TwitchClient mainAccountClient;
	private List<IChatAction> botActions;

	public TwitchClient getBotClient() {
		return botClient;
	}

	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		this.botActions = botActions;
	}

	private ResultsExporter resultsExporter;

	@Autowired
	public void setResultsExporter(ResultsExporter resultsExporter) {
		this.resultsExporter = resultsExporter;
	}

	public TwitchClient getMainAccountClient() {
		return mainAccountClient;
	}

	IDisposable goLiveListener;
	IDisposable goOfflineListener;

	public void initialize() {
		OAuth2Credential botCredential = new OAuth2Credential("twitch", authData.getBotAuthToken());
		OAuth2Credential mainAccountCredential = new OAuth2Credential("twitch", authData.getMainAccountAuthToken());

		if (mainAccountClient == null) {
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

			User something = mainAccountClient.getHelix().getUsers(authData.getMainAccountAuthToken(), null, null).execute().getUsers().get(0);
			authData.setMainAccountChannelId(something.getId());

//			GameList games = mainAccountClient.getHelix().getGames(authData.getMainAccountAuthToken(), null, Collections.singletonList("Mario Kart 8")).execute();
//
//			ChannelInformation info = new ChannelInformation()
//					.withTitle("Funktioniert mein Bot? Mainaccount");
//
//			if (games.getGames().size() > 0) {
//				info = info.withGameName(games.getGames().get(0).getName())
//						.withGameId(games.getGames().get(0).getId());
//			}

			// mainAccountClient.getHelix().updateChannelInformation(authData.getMainAccountAuthToken(), authData.getMainAccountChannelId(), info).execute();
		}

		if (botClient == null) {
			botClient = TwitchClientBuilder.builder()
					.withDefaultAuthToken(botCredential)
					.withEnableChat(true)
					.withChatAccount(botCredential)
					.withEnableHelix(true)
					.withEnablePubSub(true)
					.build();

			goLiveListener = botClient.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
				resultsExporter.start(event.getFiredAtInstant());
			});

			goOfflineListener = botClient.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
				resultsExporter.stop();
			});

//			botClient.getClientHelper().enableFollowEventListener(authData.getMainAccountUsername());
//
//			botClient.getPubSub().listenForFollowingEvents(botCredential, authData.getMainAccountChannelId());
//			botClient.getEventManager().onEvent(PrivateMessageEvent.class, (System.out::println));
//
//			botClient.getEventManager().onEvent(FollowingEvent.class, (ev -> {
//				botClient.getChat().sendPrivateMessage(ev.getData().getUsername(), "Thanks for following! Sadly, Twitch is being flooded by scam bots right now. Please write something either in chat or in this private message to confirm you're not a bot. If you don't write anything, my bot will block you after two minutes. Sorry for the inconvenience, strohkoenig.");
//			}));
//
//			botClient.getChat().sendPrivateMessage(authData.getMainAccountUsername(), "Thanks for following! Sadly, Twitch is being flooded by scam bots right now. Please write something either in chat or in this private message to confirm you're not a bot. If you don't write anything, my bot will block you after two minutes. Sorry for the inconvenience, strohkoenig.");

			botClient.getChat().joinChannel(authData.getMainAccountUsername());

			if (botClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				botClient.getChat().sendMessage(authData.getMainAccountUsername(), "Hi! strohk2Pog");
			}

//			GameList games = botClient.getHelix().getGames(authData.getMainAccountAuthToken(), null, Collections.singletonList("Super Hexagon")).execute();
//
//			ChannelInformation info = new ChannelInformation()
//					.withTitle("Funktioniert mein Bot? Botaccount");
//
//			if (games.getGames().size() > 0) {
//				info = info.withGameName(games.getGames().get(0).getName())
//						.withGameId(games.getGames().get(0).getId());
//			}

			// botClient.getHelix().updateChannelInformation(authData.getMainAccountAuthToken(), authData.getMainAccountChannelId(), info).execute();
		}
	}

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
		}

		if (mainAccountClient != null) {
			if (mainAccountClient.getChat().isChannelJoined(authData.getMainAccountUsername())) {
				mainAccountClient.getChat().sendMessage(authData.getMainAccountUsername(), "Bye!");
				mainAccountClient.getChat().leaveChannel(authData.getMainAccountUsername());
			}
		}
	}
}

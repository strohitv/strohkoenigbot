package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.RaidEvent;
import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.*;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.AutoSoAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.ChannelMessageConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.PrivateMessageConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.RaidEventConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.RewardRedeemedConsumer;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Clip;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TwitchBotClient {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private static Instant lastClipCreatedTime = Instant.now();

	private TwitchClient client;
	private static ResultsExporter resultsExporter;

	private String accessToken;

	private final String channelName = "strohkoenig";

	private final List<IChatAction> botActions = new ArrayList<>();

	@Autowired
	public void setBotActions(List<IChatAction> actions) {
		botActions.clear();
		botActions.addAll(actions);
	}

	private AutoSoAction autoSoAction;

	@Autowired
	public void setAutoSoAction(AutoSoAction autoSoAction) {
		this.autoSoAction = autoSoAction;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public static void setResultsExporter(ResultsExporter resultsExporter) {
		TwitchBotClient.resultsExporter = resultsExporter;
	}

	public TwitchClient getClient() {
		return client;
	}

	private final TwitchAuthRepository twitchAuthRepository;

	@Autowired
	public TwitchBotClient(TwitchAuthRepository twitchAuthRepository) {
		this.twitchAuthRepository = twitchAuthRepository;
		initializeClient();
	}

	IDisposable goLiveListener;
	IDisposable goOfflineListener;

	public void initializeClient() {
		OAuth2Credential botCredential = getBotCredential();
		if (botCredential == null) return;

		try {
			TwitchClientBuilder builder = TwitchClientBuilder.builder()
					.withDefaultAuthToken(botCredential)
					.withEnableChat(true)
					.withChatAccount(botCredential)
					.withEnableHelix(true)
					.withEnablePubSub(true);

			client = builder.build();

			client.getClientHelper().enableStreamEventListener(channelName);
			client.getClientHelper().enableFollowEventListener(channelName);

			client.getChat().joinChannel(channelName);
			if (client.getChat().isChannelJoined(channelName)) {
				client.getChat().sendMessage(channelName, "Bot started. Hi! strohk2PogFree");
			}

			client.getPubSub().listenForChannelPointsRedemptionEvents(botCredential, "38502044");

			goLiveListener = client.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
				if (resultsExporter != null) {
					Account account = accountRepository.findByTwitchUserId(event.getChannel().getId()).orElse(new Account());
					resultsExporter.start(account);
				}

				autoSoAction.startStream();
			});

			goOfflineListener = client.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
				if (resultsExporter != null) {
					Account account = accountRepository.findByTwitchUserId(event.getChannel().getId()).orElse(new Account());
					resultsExporter.stop(account);
				}

				autoSoAction.endStream();
			});

			client.getEventManager().onEvent(RaidEvent.class, new RaidEventConsumer(botActions));
			client.getEventManager().onEvent(RewardRedeemedEvent.class, new RewardRedeemedConsumer(botActions));
			client.getEventManager().onEvent(ChannelMessageEvent.class, new ChannelMessageConsumer(botActions));
			client.getEventManager().onEvent(PrivateMessageEvent.class, new PrivateMessageConsumer(botActions));
		} catch (Exception ignored) {
		}
	}

	@Nullable
	private OAuth2Credential getBotCredential() {
		TwitchAuth twitchAuth = this.twitchAuthRepository.findAll().stream().findFirst().orElse(null);

		if (twitchAuth == null) {
			return null;
		}

		accessToken = twitchAuth.getToken();
		return new OAuth2Credential("twitch", accessToken);
	}

	public void joinChannel(String channelName) {
		if (!client.getChat().isChannelJoined(channelName)) {
			client.getChat().joinChannel(channelName);
			client.getChat().sendMessage(channelName, "I'm here now, hi! :D");

			User user = client.getHelix().getUsers(null, null, Collections.singletonList(channelName)).execute().getUsers().stream().findFirst().orElse(null);
			if (user != null) {
				OAuth2Credential botCredential = getBotCredential();
				if (botCredential == null) return;

				client.getPubSub().listenForChannelPointsRedemptionEvents(botCredential, user.getId());
			}
		}
	}

	public void leaveChannel(String channelName) {
		if (client.getChat().isChannelJoined(channelName)) {
			client.getChat().sendMessage(channelName, "I'm leaving now, bye! :D");
			client.getChat().leaveChannel(channelName);
		}
	}

	public boolean isChannelJoined(String channelName) {
		return client.getChat().isChannelJoined(channelName);
	}

	public Splatoon2Clip createClip(String message, String channelId, boolean isGoodPlay) {
		StreamList liveStreams = client.getHelix().getStreams(accessToken, null, null, null, null, null, Collections.singletonList(channelId), null).execute();

		if (liveStreams.getStreams().size() == 0) {
			logger.warn("Can't create clip -> stream not running");
			return null;
		}

		if (channelId == null || channelId.isBlank()) {
			logger.warn("Can't create clip -> channel not found");
			return null;
		}

		if (Instant.now().isBefore(lastClipCreatedTime.plus(20, ChronoUnit.SECONDS))) {
			logger.warn("Can't create clip -> a clip has already been created in the last 20 seconds");
			logger.warn("Current time: {} - last created Clip: {}", Instant.now(), lastClipCreatedTime);
			return null;
		}

		lastClipCreatedTime = Instant.now();
		logger.info("Creating clip at time: {}", lastClipCreatedTime);

		Splatoon2Clip clip = null;

		try {
			CreateClipList newClip = client.getHelix().createClip(accessToken, channelId, false).execute();

			List<String> ids = new ArrayList<>();
			newClip.getData().forEach(c -> ids.add(c.getId()));

			logger.info("Created clip ids: {}", ids);

			if (ids.size() > 0) {
				String id = ids.get(0);

				ClipList list;
				int attempt = 1;

				while ((list = client.getHelix().getClips(null, null, null, id, null, null, null, null, null).execute()).getData().size() == 0) {
					try {
						logger.info("attempt number: {}", attempt);
						attempt++;
						Thread.sleep(1000);
					} catch (Exception ignored) {
					}
				}

				if (list.getData().size() > 0) {
					Clip loadedClip = list.getData().get(0);

					clip = new Splatoon2Clip();
					clip.setStartTime(loadedClip.getCreatedAtInstant().getEpochSecond() - 30);
					clip.setEndTime(loadedClip.getCreatedAtInstant().getEpochSecond());
					clip.setDescription(message);
					clip.setIsGoodPlay(isGoodPlay);
					clip.setClipUrl(loadedClip.getUrl());

					logger.info("Created clip: {}", clip);
				} else {
					logger.warn("Couldn't load the clip with id: {}", ids.get(0));
				}
			} else {
				logger.warn("Didn't receive any clip ids!!");
			}
		} catch (Exception ex) {
			// for example: Stream is not live
			logger.error("clip creation failed due to an exception");
			logger.error(ex);
		}

		return clip;
	}

	@PreDestroy
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
				client.getChat().sendMessage(channelName, "Stopping Bot. Bye! strohk2UwuFree");
				client.getChat().leaveChannel(channelName);
			}

			client.getEventManager().getActiveSubscriptions().forEach(IDisposable::dispose);
			client.getEventManager().close();
			client.close();

			client = null;
		}
	}
}

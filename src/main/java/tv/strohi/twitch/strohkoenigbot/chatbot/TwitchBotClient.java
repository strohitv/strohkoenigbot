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
import com.github.twitch4j.helix.domain.Clip;
import com.github.twitch4j.helix.domain.ClipList;
import com.github.twitch4j.helix.domain.CreateClipList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.AutoSoAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonClip;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class TwitchBotClient {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private static String mainAccountId;
	private static boolean isStreamRunning = false;
	private static Instant lastClipCreatedTime = Instant.now();

	private TwitchClient client;
	private final ResultsExporter resultsExporter;
	private final List<IChatAction> botActions;
	private final String channelName;
	private AutoSoAction autoSoAction;

	private String accessToken;

	public TwitchBotClient(ResultsExporter resultsExporter, List<IChatAction> botActions, String channelName, AutoSoAction autoSoAction) {
		this.resultsExporter = resultsExporter;
		this.botActions = botActions;
		this.channelName = channelName;
		this.autoSoAction = autoSoAction;
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
//		if (client == null) {
		try {
			accessToken = auth.getToken();
			OAuth2Credential botCredential = new OAuth2Credential("twitch", accessToken);

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
				client.getClientHelper().enableStreamEventListener(channelName);
				client.getClientHelper().enableFollowEventListener(channelName);

				goLiveListener = client.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
					isStreamRunning = true;
					resultsExporter.start();
					autoSoAction.startStream();
				});

				goOfflineListener = client.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
					isStreamRunning = false;
					resultsExporter.stop();
					autoSoAction.endStream();
				});

				client.getEventManager().onEvent(RaidEvent.class, raidEvent -> {
					ActionArgs args = new ActionArgs();

					args.setReason(TriggerReason.Raid);
					args.setUser(raidEvent.getRaider().getName());
					args.setUserId(raidEvent.getRaider().getId());

					args.getArguments().put(ArgumentKey.Event, raidEvent);

					args.getArguments().put(ArgumentKey.ChannelId, raidEvent.getChannel().getId());
					args.getArguments().put(ArgumentKey.ChannelName, raidEvent.getChannel().getName());

					botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.Raid)).forEach(action -> action.run(args));
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

					args.setReplySender(
							new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
					);

					botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
				});

				client.getEventManager().onEvent(PrivateMessageEvent.class, event -> {
					ActionArgs args = new ActionArgs();

					args.setReason(TriggerReason.PrivateMessage);
					args.setUser(event.getUser().getName());
					args.setUserId(event.getUser().getId());

					args.getArguments().put(ArgumentKey.Event, event);
					args.getArguments().put(ArgumentKey.Message, event.getMessage());
					args.getArguments().put(ArgumentKey.ChannelName, event.getUser().getName());

					args.setReplySender(
							new TwitchDiscordMessageSender(TwitchMessageSender.getBotTwitchMessageSender(), null, args)
					);

					botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.ChatMessage)).forEach(action -> action.run(args));
				});
			} else {
				// main account => save id
				mainAccountId = auth.getChannelId();
			}

			client.getChat().joinChannel(channelName);
			if (client.getChat().isChannelJoined(channelName)) {
				client.getChat().sendMessage(channelName, "Hi! strohk2PogFree");
			}
		} catch (Exception ignored) {

		}
//		}
	}

	public SplatoonClip createClip(String message, boolean isGoodPlay) {
		if (!isStreamRunning) {
			logger.warn("Can't create clip -> stream not running");
			return null;
		}

		if (Instant.now().isBefore(lastClipCreatedTime.plus(20, ChronoUnit.SECONDS))) {
			logger.warn("Can't create clip -> a clip has already been created in the last 20 seconds");
			logger.warn("Current time: {} - last created Clip: {}", Instant.now(), lastClipCreatedTime);
			return null;
		}

		lastClipCreatedTime = Instant.now();
		logger.info("Creating clip at time: {}", lastClipCreatedTime);

		SplatoonClip clip = null;

		try {
			CreateClipList newClip = client.getHelix().createClip(accessToken, mainAccountId, false).execute();

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

					clip = new SplatoonClip();
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
				client.getChat().sendMessage(channelName, "Bye! strohk2UwuFree");
				client.getChat().leaveChannel(channelName);
			}

			client.getEventManager().getActiveSubscriptions().forEach(IDisposable::dispose);
			client.getEventManager().close();
			client.close();

			client = null;
		}
	}
}

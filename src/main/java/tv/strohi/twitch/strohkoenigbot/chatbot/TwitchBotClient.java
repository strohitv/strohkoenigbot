package tv.strohi.twitch.strohkoenigbot.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.RaidEvent;
import com.github.twitch4j.common.events.user.PrivateMessageEvent;
import com.github.twitch4j.events.ChannelClipCreatedEvent;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.eventsub.events.ChannelAdBreakBeginEvent;
import com.github.twitch4j.helix.domain.Clip;
import com.github.twitch4j.helix.domain.ClipList;
import com.github.twitch4j.helix.domain.CreateClipList;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.pubsub.events.AdsScheduleUpdateEvent;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.AutoSoAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.ChannelMessageConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.PrivateMessageConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.RaidEventConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.consumer.RewardRedeemedConsumer;
import tv.strohi.twitch.strohkoenigbot.chatbot.model.*;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAccess;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchGoingLiveAlert;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Clip;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAccessRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchGoingLiveAlertRepository;
import tv.strohi.twitch.strohkoenigbot.obs.ObsController;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;
import tv.strohi.twitch.strohkoenigbot.utils.Constants;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.annotation.PreDestroy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class TwitchBotClient implements ScheduledService {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final List<Consumer<ChannelGoLiveEvent>> goingLiveAlertConsumers = new ArrayList<>();

	private static Instant lastClipCreatedTime = Instant.now();

	@Getter
	private Instant wentLiveTime = null;
	private final LogSender logSender;

	public void forceLive() {
		wentLiveTime = Instant.now();
	}

	public void forceOffline() {
		wentLiveTime = null;
	}

	private final List<TwitchAccessInformation> twitchClients = new ArrayList<>();

	public TwitchClient getMessageClient() {
		return twitchClients.stream()
			.filter(tc -> tc.getAccess().getUseForMessages())
			.findFirst()
			.map(TwitchAccessInformation::getClient)
			.orElse(null);
	}

	public TwitchAccessInformation getMessageConnection() {
		return twitchClients.stream()
			.filter(tc -> tc.getAccess().getUseForMessages())
			.findFirst()
			.orElse(null);
	}

	public Optional<TwitchClient> getClient(String userId) {
		return twitchClients.stream()
			.filter(tc -> Objects.equals(tc.getAccess().getUserId(), userId))
			.findFirst()
			.map(TwitchAccessInformation::getClient);
	}

	private static ResultsExporter resultsExporter;

	private final Map<String, Queue<ChannelClipCreatedEvent>> createdClips = Map.of("strohkoenig", new LinkedList<>(), "stroh_ohne_i", new LinkedList<>());

	public Optional<ChannelClipCreatedEvent> pollCreatedClip(String channelName) {
		if (Constants.ALL_TWITCH_CHANNEL_NAMES.contains(channelName)) {
			var list = createdClips.get(channelName);

			return Optional.ofNullable(list.poll());
		}

		return Optional.empty();
	}

	private final Queue<TwitchClientGoLiveChannel> goLiveEventsToChange = new LinkedList<>();

	@Setter
	private boolean fakeDebug = false;

	private final List<IChatAction> botActions = new ArrayList<>();

	private String lastStateNonce = null;

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

	private final TwitchAccessRepository twitchAccessRepository;
	private final TwitchGoingLiveAlertRepository twitchGoingLiveAlertRepository;
	private final ConfigurationRepository configurationRepository;

	@Autowired
	public TwitchBotClient(TwitchGoingLiveAlertRepository twitchGoingLiveAlertRepository, ConfigurationRepository configurationRepository, TwitchAccessRepository twitchAccessRepository, LogSender logSender) {
		this.twitchGoingLiveAlertRepository = twitchGoingLiveAlertRepository;
		this.configurationRepository = configurationRepository;
		this.twitchAccessRepository = twitchAccessRepository;

		this.logSender = logSender;

		twitchAccessRepository.findByUseForMessages(true)
			.ifPresent(access -> {
				var refreshedAccessToken = refreshAccessToken(access);

				if (refreshedAccessToken != null) {
					access.setAccessToken(refreshedAccessToken);
					twitchAccessRepository.save(access);
				}

				var newBotCredential = new OAuth2Credential("twitch", access.getAccessToken(), access.getRefreshToken(), access.getUserId(), access.getPreferredUsername(), access.getExpiresIn(), access.getScopesList(), null);
				var twitchClient = initializeClient(newBotCredential, access);

				twitchClients.add(new TwitchAccessInformation(access, twitchClient, newBotCredential));
			});
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
				.name("TwitchBotClient_initializeOneClient")
				.schedule(TickSchedule.getScheduleString(TickSchedule.everyMinutes(1)))
				.runnable(this::initializeOneClient)
				.build(),
			ScheduleRequest.builder()
				.name("TwitchBotClient_addOneGoLiveEventListener")
				.schedule(TickSchedule.getScheduleString(1))
				.runnable(this::addOneGoLiveEvent)
				.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	public void addGoingLiveAlertConsumer(Consumer<ChannelGoLiveEvent> consumer) {
		if (!goingLiveAlertConsumers.contains(consumer)) {
			goingLiveAlertConsumers.add(consumer);
		}
	}

	IDisposable goLiveListener;
	IDisposable goOfflineListener;

	public TwitchClient initializeClient(OAuth2Credential botCredential, TwitchAccess access) {
		if (botCredential == null) return null;

		try {
			TwitchClientBuilder builder = TwitchClientBuilder.builder()
				.withDefaultAuthToken(botCredential)
				.withEnableChat(true)
				.withChatAccount(botCredential)
				.withEnableHelix(true)
				.withEnablePubSub(true);

			var client = builder.build();

			if (access.getUseForMessages()) {
				for (var channelName : Constants.ALL_TWITCH_CHANNEL_NAMES) {
					client.getChat().joinChannel(channelName);
					if (client.getChat().isChannelJoined(channelName)) {
						client.getChat().sendMessage(channelName, "Bot started. Hi! DinoDance");
					}

					client.getClientHelper().enableStreamEventListener(channelName);
					client.getClientHelper().enableFollowEventListener(channelName);
					client.getClientHelper().enableClipEventListener(channelName);

					client.getPubSub().listenForChannelPointsRedemptionEvents(botCredential, access.getUserId());
					client.getEventManager().onEvent(RewardRedeemedEvent.class, new RewardRedeemedConsumer(botActions));
				}

				var allAlerts = twitchGoingLiveAlertRepository.findAll().stream().map(TwitchGoingLiveAlert::getTwitchChannelName).distinct().collect(Collectors.toList());
				logSender.sendLogs(logger, String.format("number of twitch alerts to watch over: %d", allAlerts.size()));

				for (var alertChannelName : allAlerts) {
					goLiveEventsToChange.add(new TwitchClientGoLiveChannel(client, alertChannelName, true));
				}

				goLiveListener = client.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
					logSender.sendLogs(logger, String.format("alert fired for channel: %s", event.getChannel().getName()));
					for (var consumer : goingLiveAlertConsumers) {
						consumer.accept(event);
					}

					if (Constants.ALL_TWITCH_CHANNEL_NAMES.contains(event.getChannel().getName())) {
						logSender.sendLogs(logger, String.format("going live for channel: %s", event.getChannel().getName()));
						goLive(event.getChannel().getId());
					}
				});

				goOfflineListener = client.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
					if (Constants.ALL_TWITCH_CHANNEL_NAMES.contains(event.getChannel().getName())) {
						logSender.sendLogs(logger, String.format("going offline for channel: %s", event.getChannel().getName()));
						goOffline(event.getChannel().getId());
					}
				});

				client.getEventManager().onEvent(ChannelClipCreatedEvent.class, event -> {
					var channelName = event.getChannel().getName();
					if (Constants.ALL_TWITCH_CHANNEL_NAMES.contains(channelName)) {
						logger.info("Adding clip for channel {}, url {}", event.getChannel().getName(), event.getClip().getUrl());
						createdClips.get(channelName).add(event);
					}
				});

				client.getEventManager().onEvent(RaidEvent.class, new RaidEventConsumer(botActions));
				client.getEventManager().onEvent(ChannelMessageEvent.class, new ChannelMessageConsumer(botActions));
				client.getEventManager().onEvent(PrivateMessageEvent.class, new PrivateMessageConsumer(botActions));
				logSender.sendLogs(logger, "done with twitch events");
			} else {
				client.getPubSub().listenForAdsManagerEvents(botCredential, access.getUserId(), access.getUserId());
				client.getPubSub().listenForAdsEvents(botCredential, access.getUserId());

				client.getPubSub().getEventManager().onEvent(ChannelAdBreakBeginEvent.class, event -> {
					if (Constants.ALL_TWITCH_CHANNEL_NAMES.contains(event.getBroadcasterUserName())) {
						if (event.getStartedAt().isBefore(Instant.now())) {
							// Ads running, send !ads
							getMessageClient().getChat().sendMessage(event.getBroadcasterUserName(), String.format("!ads @%s", event.getBroadcasterUserName()));
						} else {
							// Ads soon, notify streamer via chat
							getMessageClient().getChat().sendMessage(event.getBroadcasterUserName(), String.format("@%s AN AD BREAK WILL START SOON! Ads will start in %.2f minutes and will run for %.2f minutes.", event.getBroadcasterUserName(), Duration.between(Instant.now(), event.getStartedAt()).toSeconds() / 60.0, event.getLengthSeconds() / 60.0));
						}
					}
				});

				client.getPubSub().getEventManager().onEvent(AdsScheduleUpdateEvent.class, event -> {
					var channelName = twitchClients.stream()
						.filter(c -> Objects.equals(event.getChannelId(), c.getAccess().getUserId())).findFirst()
						.map(c -> c.getAccess().getPreferredUsername());

					if (channelName.isPresent() && Constants.ALL_TWITCH_CHANNEL_NAMES.contains(channelName.get())) {
						event.getData().getAdSchedule().stream().reduce((a, b) -> a.getRunAtTime().isBefore(b.getRunAtTime()) ? a : b)
							.ifPresent(ad -> {
								if (ad.getRunAtTime().isBefore(Instant.now())) {
									// Ads running, send !ads
									getMessageClient().getChat().sendMessage(channelName.get(), String.format("!ads @%s", channelName.get()));
								} else {
									// Ads soon, notify streamer via chat
									getMessageClient().getChat().sendMessage(channelName.get(), String.format("@%s AN AD BREAK WILL START SOON! Ads will start in %.2f minutes and will run for %.2f minutes.", channelName.get(), Duration.between(Instant.now(), ad.getRunAtTime()).toSeconds() / 60.0, ad.getDurationSeconds() / 60.0));
								}
							});
					}
				});
			}

			logSender.sendLogs(logger, "fully connected twitch bot client");

			return client;
		} catch (Exception ex) {
			logSender.sendLogs(logger, String.format("something in twitch bot client went wrong, message: `%s`. see logs for details", ex.getMessage()));
			logger.error(ex);
		}

		return null;
	}

	public void goOffline(String channelId) {
		wentLiveTime = null;

		ObsController.setIsLive(false);

		if (resultsExporter != null) {
			Account account = accountRepository.findByTwitchUserId(channelId).orElse(null);
			resultsExporter.stop(account);
		}

		autoSoAction.endStream();
	}

	public void goLive(String channelId) {
		wentLiveTime = Instant.now();

		ObsController.setIsLive(true);

		if (resultsExporter != null) {
			Account account = accountRepository.findByTwitchUserId(channelId).orElse(null);
			resultsExporter.start(account);
		}

		autoSoAction.startStream();
	}

	public String getAuthCodeGrantFlowUrl(String redirectUri) {
		var scopes = List.of(
			"openid",
			"analytics:read:extensions",
			"analytics:read:games",
			"bits:read",
			"channel:manage:ads",
			"channel:read:ads",
			"channel:manage:broadcast",
			"channel:read:charity",
			"channel:edit:commercial",
			"channel:read:editors",
			"channel:manage:extensions",
			"channel:read:goals",
			"channel:read:guest_star",
			"channel:manage:guest_star",
			"channel:read:hype_train",
			"channel:manage:moderators",
			"channel:read:polls",
			"channel:manage:polls",
			"channel:read:predictions",
			"channel:manage:predictions",
			"channel:manage:raids",
			"channel:read:redemptions",
			"channel:manage:redemptions",
			"channel:manage:schedule",
			"channel:read:stream_key",
			"channel:read:subscriptions",
			"channel:manage:videos",
			"channel:read:vips",
			"channel:manage:vips",
			"clips:edit",
			"moderation:read",
			"moderator:manage:announcements",
			"moderator:manage:automod",
			"moderator:read:automod_settings",
			"moderator:manage:automod_settings",
			"moderator:manage:banned_users",
			"moderator:read:blocked_terms",
			"moderator:manage:blocked_terms",
			"moderator:manage:chat_messages",
			"moderator:read:chat_settings",
			"moderator:manage:chat_settings",
			"moderator:read:chatters",
			"moderator:read:followers",
			"moderator:read:guest_star",
			"moderator:manage:guest_star",
			"moderator:read:shield_mode",
			"moderator:manage:shield_mode",
			"moderator:read:shoutouts",
			"moderator:manage:shoutouts",
			"moderator:read:unban_requests",
			"moderator:manage:unban_requests",
			"user:edit",
			"user:edit:follows",
			"user:read:blocked_users",
			"user:manage:blocked_users",
			"user:read:broadcast",
			"user:manage:chat_color",
			"user:read:email",
			"user:read:emotes",
			"user:read:follows",
			"user:read:moderated_channels",
			"user:read:subscriptions",
			"user:manage:whispers",
			"channel:bot",
			"channel:moderate",
			"chat:edit",
			"chat:read",
			"user:bot",
			"user:read:chat",
			"user:write:chat",
			"whispers:read",
			"whispers:edit");

		var scopeString = scopes.stream()
			.map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
			.reduce((a, b) -> String.format("%s+%s", a, b))
			.orElse("");

		lastStateNonce = UUID.randomUUID().toString();

		if (redirectUri == null || redirectUri.isBlank()) {
			redirectUri = "http://localhost:8080/twitch-api";
		}

		var claimsRequestParam = "";
		try {
			claimsRequestParam = String.format("&claims=%s", new ObjectMapper().writeValueAsString(new TwitchClaims(new TwitchClaims.TwitchClaimsIdToken())));
		} catch (JsonProcessingException e) {
			logger.error("could not parse TwitchClaims wtf", e);
		}

		return String.format("https://id.twitch.tv/oauth2/authorize" +
			"?response_type=code" +
			"%s" +
			"&client_id=xriad8eh1sxcxhoheewxe8l7ppzg50" +
			"&redirect_uri=%s" +
			"&scope=%s" +
			"&state=%s" +
			"&nonce=%s", claimsRequestParam, redirectUri, scopeString, lastStateNonce, lastStateNonce);
	}

	public void joinChannel(String channelName) {
		twitchClients.stream()
			.filter(c -> c.getAccess().getUseForMessages())
			.findFirst()
			.ifPresent(connection -> {
				var client = connection.getClient();

				if (!client.getChat().isChannelJoined(channelName)) {
					client.getChat().joinChannel(channelName);
					client.getChat().sendMessage(channelName, "I'm here now, hi! DinoDance");

					client.getHelix().getUsers(null, null, Collections.singletonList(channelName)).execute().getUsers().stream()
						.findFirst()
						.ifPresent(user -> client.getPubSub().listenForChannelPointsRedemptionEvents(connection.getToken(), user.getId()));
				}
			});
	}

	public void leaveChannel(String channelName) {
		twitchClients.stream()
			.filter(c -> c.getAccess().getUseForMessages())
			.findFirst()
			.ifPresent(connection -> {
				var client = connection.getClient();
				if (client.getChat().isChannelJoined(channelName)) {
					client.getChat().sendMessage(channelName, "I'm leaving now, bye! DinoDance");
					client.getChat().leaveChannel(channelName);
				}
			});
	}

	public void enableGoingLiveEvent(String channelName) {
		twitchClients.stream()
			.filter(c -> c.getAccess().getUseForMessages())
			.findFirst()
			.ifPresent(connection -> {
				var client = connection.getClient();
				goLiveEventsToChange.add(new TwitchClientGoLiveChannel(client, channelName, true));
			});
	}

	public void disableGoingLiveEvent(String channelName) {
		twitchClients.stream()
			.filter(c -> c.getAccess().getUseForMessages())
			.findFirst()
			.ifPresent(connection -> {
				var client = connection.getClient();
				goLiveEventsToChange.add(new TwitchClientGoLiveChannel(client, channelName, false));
			});
	}

	public boolean isChannelJoined(String channelName) {
		return twitchClients.stream()
			.filter(c -> c.getAccess().getUseForMessages())
			.findFirst()
			.map(connection -> {
				var client = connection.getClient();
				return client.getChat().isChannelJoined(channelName);
			})
			.orElse(false);
	}

	public boolean isLive(String channelId) {
		if (fakeDebug) {
			Account account = accountRepository.findByTwitchUserId(channelId).stream().findFirst().orElse(null);

			if (account != null && account.getIsMainAccount() != null && account.getIsMainAccount()) {
				return true;
			}
		}

		return isLiveIgnoreDebug(channelId);
	}

	public boolean isLiveIgnoreDebug(String channelId) {
		var client = getMessageClient();

		return channelId != null
			&& !channelId.isBlank()
			&&
			(client != null && !client.getHelix().getStreams(null, null, null, null, null, null, Collections.singletonList(channelId), null).execute()
				.getStreams().isEmpty());
	}

	public Splatoon2Clip createClip(String message, String channelId, boolean isGoodPlay) {
		if (!isLiveIgnoreDebug(channelId)) {
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
			var connection = getMessageConnection();
			if (connection == null) {
				logger.warn("Can't create clip -> there is no twitch client!!");
				return null;
			}

			CreateClipList newClip = connection.getClient().getHelix().createClip(connection.getToken().getAccessToken(), channelId, false).execute();

			List<String> ids = new ArrayList<>();
			newClip.getData().forEach(c -> ids.add(c.getId()));

			logger.info("Created clip ids: {}", ids);

			if (!ids.isEmpty()) {
				String id = ids.get(0);

				ClipList list;
				int attempt = 1;

				while ((list = connection.getClient().getHelix().getClips(null, null, null, List.of(id), null, null, null, null, null, null)
					.execute()).getData().isEmpty()) {
					try {
						if (attempt > 1) {
							logger.info("attempt number: {}", attempt);
						}
						attempt++;
						Thread.sleep(1000);
					} catch (Exception ignored) {
					}
				}

				if (!list.getData().isEmpty()) {
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

	public Optional<String> getGameName(String gameId) {
		var client = getMessageClient();
		if (client == null) {
			return Optional.empty();
		}

		return client.getHelix().getGames(null, List.of(gameId), null, null).execute().getGames().stream()
			.map(Game::getName)
			.findFirst();
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

		while (!twitchClients.isEmpty()) {
			var client = twitchClients.remove(0);
			if (client != null) {
				disconnectClient(client.getClient());
			}
		}
	}

	private void disconnectClient(TwitchClient twitchClient) {
		for (var channelName : Constants.ALL_TWITCH_CHANNEL_NAMES) {
			if (twitchClient.getChat().isChannelJoined(channelName)) {
				twitchClient.getChat().sendMessage(channelName, "Stopping Bot. Bye! DinoDance");
				twitchClient.getChat().leaveChannel(channelName);
			}
		}

		twitchClient.getEventManager().getActiveSubscriptions().forEach(IDisposable::dispose);
		twitchClient.getEventManager().close();
		twitchClient.close();
	}

	public boolean connectAccount(String state, String code) {
		if (!Objects.equals(lastStateNonce, state)) {
			return false;
		}

		var clientId = configurationRepository.findByConfigName("twitchClientId")
			.map(Configuration::getConfigValue)
			.orElse(null);

		var clientSecret = configurationRepository.findByConfigName("twitchClientPassword")
			.map(Configuration::getConfigValue)
			.orElse(null);

		if (clientId == null || clientSecret == null) {
			return false;
		}

		var body = String.format("client_id=%s" +
			"&client_secret=%s" +
			"&code=%s" +
			"&grant_type=authorization_code" +
			"&redirect_uri=http://localhost:8080/twitch-api", clientId, clientSecret, code);

		var token = postOauthToken(body);

		if (token != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(token.getAccessToken()); // todo oder idToken??
			var idToken = getUserInfo(headers);

			if (idToken != null) {
				var twitchAccess = twitchAccessRepository.findByUserId(idToken.getSub())
					.orElse(new TwitchAccess())
					.toBuilder()
					.accessToken(token.getAccessToken())
					.idToken(token.getIdToken())
					.refreshToken(token.getRefreshToken())
					.userId(idToken.getSub())
					.preferredUsername(idToken.getPreferredUsername())
					.email(idToken.getEmail())
					.emailVerified(idToken.getEmailVerified())
					.picture(idToken.getPicture())
					.updatedAt(idToken.getUpdatedAtAsDate())
					.expiresIn(token.getExpiresIn())
					.build();

				twitchAccess.setScopes(token.getScope());
				twitchAccess.setUseForMessages(Optional.ofNullable(twitchAccess.getUseForMessages()).orElse(false) || twitchAccessRepository.findAll().isEmpty());

				twitchAccess = twitchAccessRepository.save(twitchAccess);

				var oldTwitchAccessInformation = twitchClients.stream().filter(tc -> Objects.equals(tc.getAccess().getUserId(), idToken.getSub())).findFirst();
				oldTwitchAccessInformation.ifPresent(info -> {
					disconnectClient(info.getClient());
					twitchClients.remove(info);
				});

				var newBotCredential = new OAuth2Credential("twitch", token.getAccessToken(), token.getRefreshToken(), idToken.getSub(), idToken.getPreferredUsername(), token.getExpiresIn(), token.getScope(), null);
				var twitchClient = initializeClient(newBotCredential, twitchAccess);

				twitchClients.add(new TwitchAccessInformation(twitchAccess, twitchClient, newBotCredential));
			}


			return true;
		}

		return false;
	}

	private TwitchIdTokenResponse getUserInfo(HttpHeaders headers) {
		RestTemplate restTemplate = new RestTemplate();

		var entity = new HttpEntity<>(headers);

		var response = restTemplate.exchange(
			"https://id.twitch.tv/oauth2/userinfo",
			HttpMethod.GET,
			entity,
			new ParameterizedTypeReference<TwitchIdTokenResponse>() {
			});

		if (response.getStatusCode().is2xxSuccessful()) {
			return response.getBody();
		} else {
			return null;
		}
	}

	private TwitchAccessTokenResponse postOauthToken(String body) {
		RestTemplate restTemplate = new RestTemplate();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		var entity = new HttpEntity<>(body, headers);

		var response = restTemplate.exchange(
			"https://id.twitch.tv/oauth2/token",
			HttpMethod.POST,
			entity,
			new ParameterizedTypeReference<TwitchAccessTokenResponse>() {
			});

		if (response.getStatusCode().is2xxSuccessful()) {
			return response.getBody();
		} else {
			return null;
		}
	}

	private String refreshAccessToken(TwitchAccess access) {
		var clientId = configurationRepository.findByConfigName("twitchClientId")
			.map(Configuration::getConfigValue)
			.orElse(null);

		var clientSecret = configurationRepository.findByConfigName("twitchClientPassword")
			.map(Configuration::getConfigValue)
			.orElse(null);

		if (clientId == null || clientSecret == null) {
			return null;
		}

		var body = String.format("client_id=%s" +
			"&client_secret=%s" +
			"&grant_type=refresh_token" +
			"&refresh_token=%s", clientId, clientSecret, access.getRefreshToken());

		RestTemplate restTemplate = new RestTemplate();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		var entity = new HttpEntity<>(body, headers);

		var response = restTemplate.exchange(
			"https://id.twitch.tv/oauth2/token",
			HttpMethod.POST,
			entity,
			new ParameterizedTypeReference<TwitchAccessTokenResponse>() {
			});

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			return response.getBody().getAccessToken();
		} else {
			return null;
		}
	}

	private void initializeOneClient() {
		twitchAccessRepository.findAll().stream()
			.filter(access -> twitchClients.stream().noneMatch(client -> client.getAccess().getId() == access.getId()))
			.findFirst()
			.ifPresent(access -> {
				var refreshedAccessToken = refreshAccessToken(access);

				if (refreshedAccessToken != null) {
					access.setAccessToken(refreshedAccessToken);
					twitchAccessRepository.save(access);
				}

				var newBotCredential = new OAuth2Credential("twitch", access.getAccessToken(), access.getRefreshToken(), access.getUserId(), access.getPreferredUsername(), access.getExpiresIn(), access.getScopesList(), null);
				var twitchClient = initializeClient(newBotCredential, access);

				twitchClients.add(new TwitchAccessInformation(access, twitchClient, newBotCredential));
			});
	}

	private void addOneGoLiveEvent() {
		var firstEntry = goLiveEventsToChange.poll();

		if (firstEntry != null) {
			if (firstEntry.isEnable()) {
				logSender.sendLogs(logger, String.format("enabling twitch stream event listener for: %s", firstEntry.getChannelName()));
				firstEntry.getClient().getClientHelper().enableStreamEventListener(firstEntry.getChannelName());
			} else {
				logSender.sendLogs(logger, String.format("disabling twitch stream event listener for: %s", firstEntry.getChannelName()));
				firstEntry.getClient().getClientHelper().disableStreamEventListener(firstEntry.getChannelName());
			}
		}
	}
}

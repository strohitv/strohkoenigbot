package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ConnectionAccepted;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DiscordBot {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final ConfigurationRepository configurationRepository;
	private final List<IChatAction> botActions = new ArrayList<>();

	private GatewayDiscordClient gateway = null;

	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		this.botActions.clear();
		this.botActions.addAll(botActions);
	}

	private ResourcesDownloader resourcesDownloader;

	@Autowired
	public void setResourcesDownloader(ResourcesDownloader resourcesDownloader) {
		this.resourcesDownloader = resourcesDownloader;
	}

	@Autowired
	public DiscordBot(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private final List<ConnectionAccepted> subscribers = new ArrayList<>();

	public void subscribe(ConnectionAccepted callback) {
		subscribers.add(callback);
	}

	private GatewayDiscordClient getGateway() {
		List<Configuration> tokens = configurationRepository.findByConfigName("discordToken");
		if (gateway == null && tokens.size() > 0) {
			DiscordClient client = DiscordClient.create(tokens.get(0).getConfigValue());
			gateway = client.login().retry().block();

			if (gateway != null) {
				gateway.on(MessageCreateEvent.class).retry().doOnError(err -> logger.error("HARR HARR HARR DISCORD ERROR LOL", err)).subscribe(event -> {
					final Message message = event.getMessage();
					final MessageChannel channel = getChannelOfMessageWithRetries(message);

					if (channel instanceof PrivateChannel) {
						if (message.getContent().trim().toLowerCase().startsWith("yes")) {
							event.getMessage().getAuthor().ifPresent(author ->
									subscribers.forEach(s -> s.accept(author.getId().asLong()))
							);
						} else {
							event.getMessage().getAuthor().ifPresent(author -> {
										if (!"strohkoenigbot#6833".equals(author.getTag())) {
											ActionArgs args = new ActionArgs();

											args.setReason(TriggerReason.DiscordPrivateMessage);
											args.setUser(author.getTag());
											args.setUserId(author.getId().asString());

											args.getArguments().put(ArgumentKey.Event, event);
											args.getArguments().put(ArgumentKey.Message, message.getContent());

											args.setReplySender(
													new TwitchDiscordMessageSender(null, this, args)
											);

											botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.DiscordPrivateMessage)).forEach(action -> action.run(args));
										}
									}
							);
						}
					} else if (channel instanceof TextChannel) {
						// public channel on my discord
						event.getMessage().getAuthor().ifPresent(author -> {
									if (!"strohkoenigbot#6833".equals(author.getTag())) {
										TextChannel textChannel = (TextChannel) channel;

										ActionArgs args = new ActionArgs();

										args.setReason(TriggerReason.DiscordMessage);
										args.setUser(author.getTag());
										args.setUserId(author.getId().asString());

										args.getArguments().put(ArgumentKey.Event, event);
										args.getArguments().put(ArgumentKey.Message, message.getContent());
										args.getArguments().put(ArgumentKey.MessageNonce, message.getId());
										args.getArguments().put(ArgumentKey.MessageObject, message);

										args.getArguments().put(ArgumentKey.ChannelObject, textChannel);
										args.getArguments().put(ArgumentKey.ChannelName, textChannel.getName());
										args.getArguments().put(ArgumentKey.ChannelId, textChannel.getId().asString());

										args.setReplySender(
												new TwitchDiscordMessageSender(null, this, args)
										);

										botActions.stream().filter(action -> action.getCauses().contains(TriggerReason.DiscordMessage)).forEach(action -> action.run(args));
									}
								}
						);
					}
				});
			}
		}

		return gateway;
	}

	private MessageChannel getChannelOfMessageWithRetries(Message message) {
		MessageChannel channel = null;
		Exception lastException = null;
		int attempts = 0;

		while (attempts++ < 10 && channel == null) {
			try {
				logger.info("attempt number {}", attempts);
				channel = message.getChannel().retry().block();
			} catch (Exception ex) {
				lastException = ex;

				logger.error("SOMETHING WENT WRONG WTF???");
				logger.error(message);
				logger.error(ex);

				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {
				}
			}
		}

		if (channel == null) throw new RuntimeException(lastException);

		return channel;
	}

	public Long loadUserIdFromDiscordServer(String username) {
		if (getGateway() == null) {
			return null;
		}

		Long result = null;

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && guilds.size() > 0) {
			List<Member> allMembersOfAllServers = guilds.stream()
					.flatMap(g -> Optional.ofNullable(g.getMembers(EntityRetrievalStrategy.REST).retry().collectList().block()).orElse(new ArrayList<>()).stream())
					.collect(Collectors.toList());

			result = allMembersOfAllServers.stream()
					.filter(m -> String.format("%s#%s", m.getMemberData().user().username(), m.getMemberData().user().discriminator()).equals(username))
					.map(m -> m.getId().asLong())
					.findFirst()
					.orElse(null);
		}

		return result;
	}

	public String loadUserNameFromServer(long id) {
		if (getGateway() == null) {
			return null;
		}

		String result = null;

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && guilds.size() > 0) {
			List<Member> allMembersOfAllServers = guilds.stream()
					.flatMap(g -> Optional.ofNullable(g.getMembers(EntityRetrievalStrategy.REST).retry().collectList().block()).orElse(new ArrayList<>()).stream())
					.collect(Collectors.toList());

			result = allMembersOfAllServers.stream()
					.filter(m -> m.getId().asLong() == id)
					.map(m -> String.format("%s#%s", m.getMemberData().user().username(), m.getMemberData().user().discriminator()))
					.findFirst()
					.orElse(null);
		}

		return result;
	}

	public boolean sendServerMessageWithImages(String channelName, String message, String... imageUrls) {
		if (getGateway() == null) {
			return false;
		}

		if (message == null) {
			message = "ERROR: Message was NULL!";
		}

		boolean result = false;

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && guilds.size() > 0) {
			List<GuildChannel> allChannelsOfAllServers = guilds.stream()
					.flatMap(g -> Optional.ofNullable(g.getChannels().retry().collectList().block()).orElse(new ArrayList<>()).stream())
					.collect(Collectors.toList());

			List<TextChannel> allChannels = allChannelsOfAllServers.stream()
					.filter(c -> c.getName().equals(channelName))
					.filter(c -> c instanceof TextChannel)
					.map(c -> (TextChannel) c)
					.collect(Collectors.toList());

			for (TextChannel channel : allChannels) {
				if (channel != null) {
					result = sendMessage(channel, message, imageUrls);
					logger.info("sent message to server channel '{}': message: '{}'", channel.getName(), message);
				}
			}
		}

		return result;
	}

	public boolean sendPrivateMessageWithImages(Long userId, String message, String... imageUrls) {
		if (userId == null || getGateway() == null) {
			return false;
		}

		boolean result = false;

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && guilds.size() > 0) {
			PrivateChannel channel = getPrivateChannelForUserInGuild(userId, guilds);

			if (channel != null) {
				result = sendMessage(channel, message, imageUrls);
				logger.info("sent message to server channel '{}': message: '{}'", userId, message);
			}
		}

		return result;
	}

	private boolean sendMessage(MessageChannel channel, String message, String... imageUrls) {
		MessageCreateMono createMono = channel.createMessage(message);

		try {
			List<Tuple<String, InputStream>> streams = new ArrayList<>();

			for (String imageUrlFullPath : imageUrls) {
				String imageLocationString = resourcesDownloader.ensureExistsLocally(imageUrlFullPath);
				String path = Paths.get(imageLocationString).toString();
				String idStr = Paths.get(path).getFileName().toString();

				Tuple<String, InputStream> filenameWithInputStream;
				if (imageLocationString.startsWith("https://")) {
					URL url = new URL(imageLocationString);
					filenameWithInputStream = new Tuple<>(idStr, url.openStream());
				} else {
					filenameWithInputStream = new Tuple<>(idStr, new FileInputStream(Paths.get(System.getProperty("user.dir"), path).toString()));
				}

				streams.add(filenameWithInputStream);
			}

			createMono = createMono.withFiles(
					streams.stream()
							.map(s -> MessageCreateFields.File.of(s.x, s.y))
							.collect(Collectors.toList())
			);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Message msg = createMono.retry().block();
		logger.info("sent message to server channel '{}': message: '{}'", channel.getId().asLong(), message);
		return msg != null;
	}

	private PrivateChannel getPrivateChannelForUserInGuild(Long userId, List<Guild> guilds) {
		List<Member> allMembersOfAllServers = guilds.stream()
				.flatMap(g -> Optional.ofNullable(g.getMembers(EntityRetrievalStrategy.REST).retry().collectList().block()).orElse(new ArrayList<>()).stream())
				.collect(Collectors.toList());

		return allMembersOfAllServers.stream()
				.filter(m -> m.getId().asLong() == userId)
				.findFirst()
				.flatMap(member -> getGateway()
						.getUserById(member.getId())
						.retry()
						.onErrorResume(e -> Mono.empty())
						.blockOptional()
						.flatMap(u -> u.getPrivateChannel().retry().onErrorResume(e -> Mono.empty()).blockOptional()))
				.stream()
				.findFirst()
				.orElse(null);
	}

	public boolean sendPrivateMessage(Long userId, String message) {
		if (userId == null || getGateway() == null) {
			return false;
		}

		boolean result = false;

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && guilds.size() > 0) {
			PrivateChannel channel = getPrivateChannelForUserInGuild(userId, guilds);
			if (channel != null) {
				Message msg = channel.createMessage(message).retry().onErrorResume(e -> Mono.empty()).block();
				result = msg != null;
				logger.info("sent message to server channel '{}': message: '{}'", channel.getId().asLong(), message);
			}
		}

		return result;
	}

	public void sendPrivateMessageWithAttachment(Long userId, String message, String fileName, InputStream content) {
		if (userId == null || getGateway() == null) {
			return;
		}

		List<Guild> guilds = getGateway().getGuilds().collectList().retry().block();
		if (guilds != null && guilds.size() > 0) {
			PrivateChannel channel = getPrivateChannelForUserInGuild(userId, guilds);
			if (channel != null) {
				channel.createMessage(message).withFiles(MessageCreateFields.File.of(fileName, content)).retry().onErrorResume(e -> Mono.empty()).block();
				logger.info("sent message to server channel '{}': message: '{}'", channel.getId().asLong(), message);
			}
		}
	}

	public void reply(String message, TextChannel channel, Snowflake reference) {
		channel.createMessage(MessageCreateSpec.create().withMessageReference(reference).withContent(message)).retry().onErrorResume(e -> Mono.empty()).block();
	}

	private static class Tuple<X, Y> {
		public final X x;
		public final Y y;

		public Tuple(X x, Y y) {
			this.x = x;
			this.y = y;
		}
	}
}

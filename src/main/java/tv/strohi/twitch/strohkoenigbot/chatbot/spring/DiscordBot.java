package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.core.spec.MessageCreateFields;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Attachment;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.DiscordMessageEvent;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class DiscordBot {
	private final ApplicationEventPublisher eventPublisher;

	private final ConfigurationRepository configurationRepository;
	private final AccountRepository accountRepository;

	private List<Long> adminIds = null;

	public List<Long> getAdminIds() {
		if (adminIds == null) {
			var allAdminConfigs = configurationRepository.findAllByConfigName("DiscordBot_admin")
				.stream()
				.map(Configuration::getConfigValue)
				.collect(Collectors.toList());

			if (allAdminConfigs.isEmpty()) {
				allAdminConfigs = Stream.of(
						configurationRepository.save(Configuration.builder()
							.configName("DiscordBot_admin")
							.configValue("strohkoenig")
							.build())
					)
					.map(Configuration::getConfigValue)
					.collect(Collectors.toList());
			}

			var mutableAdminIds = new HashSet<Long>();

			for (var admin : allAdminConfigs) {
				if (admin.matches("^[0-9]+$") && searchUsername(Long.parseLong(admin)).isPresent()) {
					mutableAdminIds.add(Long.parseLong(admin));
				} else {
					searchUserId(admin).ifPresent(mutableAdminIds::add);
				}
			}

			if (!mutableAdminIds.isEmpty()) {
				adminIds = List.copyOf(mutableAdminIds);
			}
		}

		return adminIds;
	}

	private GatewayDiscordClient gateway = null;

	private ResourcesDownloader resourcesDownloader;

	@Autowired
	public void setResourcesDownloader(ResourcesDownloader resourcesDownloader) {
		this.resourcesDownloader = resourcesDownloader;
	}

	private GatewayDiscordClient getGateway() {
		var tokens = configurationRepository.findAllByConfigName("discordToken");
		if (gateway == null && !tokens.isEmpty()) {
			var client = DiscordClient.create(tokens.get(0).getConfigValue());
			gateway = client.gateway()
				.setEnabledIntents(IntentSet.nonPrivileged().or(IntentSet.of(Intent.MESSAGE_CONTENT)))
				.login()
				.retry(5)
				.block();

			if (gateway != null) {
				gateway.on(MessageCreateEvent.class)
					.retry(5)
					.doOnError(err -> log.error("HARR HARR HARR DISCORD ERROR LOL", err))
					.subscribe(event -> {
						final var message = event.getMessage();
						final var channel = getChannelOfMessageWithRetries(message);

						event
							.getMessage()
							.getAuthor()
							.ifPresent(author -> eventPublisher.publishEvent(
								new DiscordMessageEvent(
									this,
									event,
									channel,
									author,
									adminIds.contains(author.getId().asLong()))));
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
				if (attempts > 1) {
					log.info("attempt number {}", attempts);
				}
				channel = message.getChannel().retry(5).block();
			} catch (Exception ex) {
				lastException = ex;

				log.error("SOMETHING WENT WRONG WTF???");
				log.error(message);
				log.error(ex);

				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {
				}
			}
		}

		if (channel == null) throw new RuntimeException(lastException);

		return channel;
	}

	public List<Long> searchServerChannelIds(String channelName) {
		if (getGateway() == null) {
			return List.of();
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			return guilds.stream()
				.map(g -> g.getChannels().retry(5).onErrorResume(e -> Mono.empty()).collectList().block())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.filter(c -> c instanceof TextChannel)
				.map(c -> (TextChannel) c)
				.filter(c -> c.getName().equals(channelName))
				.map(c -> c.getId().asLong())
				.collect(Collectors.toList());
		}

		return List.of();
	}

	public Optional<Long> searchUserId(String username) {
		if (getGateway() == null) {
			return Optional.empty();
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			var allMembersOfAllServers = guilds.stream()
				.map(g -> g.getMembers(EntityRetrievalStrategy.REST).retry(5).collectList().block())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());

			return allMembersOfAllServers.stream()
				.filter(m -> {
					var discriminator = m.getMemberData().user().discriminator();

					return (m.getMemberData().user().username().equals(username) && (discriminator == null || discriminator.equals("0") || discriminator.isBlank()))
						|| String.format("%s#0", m.getMemberData().user().username()).equals(username)
						|| String.format("%s#%s", m.getMemberData().user().username(), discriminator).equals(username);
				})
				.map(m -> m.getId().asLong())
				.findFirst();
		}

		return Optional.empty();
	}

	public Optional<String> searchUsername(long id) {
		if (getGateway() == null) {
			return Optional.empty();
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			var allMembersOfAllServers = guilds.stream()
				.map(g -> g.getMembers(EntityRetrievalStrategy.REST).retry(5).collectList().block())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());

			return allMembersOfAllServers.stream()
				.filter(m -> m.getId().asLong() == id)
				.map(m -> {
					var discriminator = m.getMemberData().user().discriminator();

					if (discriminator == null || discriminator.isBlank() || discriminator.equals("0")) {
						return m.getMemberData().user().username();
					} else {
						return String.format("%s#%s", m.getMemberData().user().username(), discriminator);
					}
				})
				.findFirst();
		}

		return Optional.empty();
	}

	public boolean sendServerMessageWithImageUrls(String channelName, String message, String... imageUrls) {
		return sendServerMessageWithImageUrls(channelName, message, true, imageUrls);
	}

	public boolean sendServerMessageWithImageUrls(String channelName, String message, boolean storeOnLocalDrive, String... imageUrls) {
		if (getGateway() == null) {
			return false;
		}

		if (message == null) {
			message = "sendServerMessageWithImageUrls(String channelName, String message, boolean storeOnLocalDrive, String... imageUrls) **ERROR**: message was NULL!";
		}

		var result = false;

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			var allChannelsOfAllServers = loadChannelsFromGuilds(guilds);

			var allChannels = allChannelsOfAllServers.stream()
				.filter(c -> c.getName().equals(channelName))
				.filter(c -> c instanceof TextChannel)
				.map(c -> (TextChannel) c)
				.collect(Collectors.toList());

			for (var channel : allChannels) {
				if (channel != null) {
					result = sendMessage(channel, message, storeOnLocalDrive, imageUrls);
					log.info("sendServerMessageWithImageUrls(String channelName, String message, boolean storeOnLocalDrive, String... imageUrls): sent message to server channel '{}': message: '{}'", channel.getName(), message);
				}
			}
		}

		return result;
	}

	private @NonNull List<GuildChannel> loadChannelsFromGuilds(List<Guild> guilds) {
		return guilds.stream()
			.map(g -> g.getChannels().retry(5).onErrorResume(e -> Mono.empty()).collectList().block())
			.filter(Objects::nonNull)
			.flatMap(Collection::stream)
			.collect(Collectors.toList());
	}

	public boolean sendServerMessageWithImages(String channelName, String message, BufferedImage... images) {
		if (getGateway() == null) {
			return false;
		}

		if (message == null) {
			message = "sendServerMessageWithImages(String channelName, String message, BufferedImage... images) **ERROR**: Message was NULL!";
		}

		var result = false;

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			var allChannelsOfAllServers = loadChannelsFromGuilds(guilds);

			var allChannels = allChannelsOfAllServers.stream()
				.filter(c -> c.getName().equals(channelName))
				.filter(c -> c instanceof TextChannel)
				.map(c -> (TextChannel) c)
				.collect(Collectors.toList());

			for (TextChannel channel : allChannels) {
				if (channel != null) {
					result = sendMessage(channel, message, images);
					log.info("sendServerMessageWithImages(String channelName, String message, BufferedImage... images): sent message to server channel '{}': message: '{}'", channel.getName(), message);
				}
			}
		}

		return result;
	}

	public boolean sendServerMessageWithImageUrls(long guildId, long channelId, String message, String... imageUrls) {
		if (getGateway() == null) {
			return false;
		}

		if (message == null) {
			message = "sendServerMessageWithImageUrls(long guildId, long channelId, String message, String... imageUrls) **ERROR**: Message was NULL!";
		}

		var result = false;

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			var allChannelsOfAllServers = guilds.stream()
				.filter(g -> g.getId().asLong() == guildId)
				.map(g -> g.getChannels().retry(5).onErrorResume(e -> Mono.empty()).collectList().block())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());

			var allChannels = allChannelsOfAllServers.stream()
				.filter(c -> c.getId().asLong() == channelId)
				.filter(c -> c instanceof TextChannel)
				.map(c -> (TextChannel) c)
				.collect(Collectors.toList());

			for (TextChannel channel : allChannels) {
				if (channel != null) {
					result = sendMessage(channel, message, imageUrls);
					log.info("sendServerMessageWithImageUrls(long guildId, long channelId, String message, String... imageUrls): sent message to server channel '{}': message: '{}'", channel.getName(), message);
				}
			}
		}

		return result;
	}

	public boolean sendServerMessageWithImages(long guildId, long channelId, String message, BufferedImage... imageUrls) {
		if (getGateway() == null) {
			return false;
		}

		if (message == null) {
			message = "sendServerMessageWithImages(long guildId, long channelId, String message, BufferedImage... imageUrls) **ERROR**: Message was NULL!";
		}

		boolean result = false;

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			List<GuildChannel> allChannelsOfAllServers = guilds.stream()
				.filter(g -> g.getId().asLong() == guildId)
				.map(g -> g.getChannels().retry(5).onErrorResume(e -> Mono.empty()).collectList().block())
				.filter(Objects::nonNull)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());

			List<TextChannel> allChannels = allChannelsOfAllServers.stream()
				.filter(c -> c.getId().asLong() == channelId)
				.filter(c -> c instanceof TextChannel)
				.map(c -> (TextChannel) c)
				.collect(Collectors.toList());

			for (TextChannel channel : allChannels) {
				if (channel != null) {
					result = sendMessage(channel, message, imageUrls);
					log.info("sendServerMessageWithImages(long guildId, long channelId, String message, BufferedImage... imageUrls): sent message to server channel '{}': message: '{}'", channel.getName(), message);
				}
			}
		}

		return result;
	}

	public void sendPrivateMessageWithImageUrls(Long userId, String message, String... imageUrls) {
		if (userId == null || getGateway() == null) {
			return;
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			getPrivateChannelForUserInGuild(userId, guilds)
				.ifPresent(channel -> {
					sendMessage(channel, message, imageUrls);
					log.info("sendPrivateMessageWithImageUrls(Long userId, String message, String... imageUrls): sent message to server channel '{}': message: '{}'", userId, message);
				});
		}
	}

	private boolean sendMessage(MessageChannel channel, String message, String... imageUrls) {
		return sendMessage(channel, message, true, imageUrls);
	}

	private boolean sendMessage(MessageChannel channel, String message, boolean storeOnLocalDrive, String... imageUrls) {
		var streams = new ArrayList<Attachment>();

		for (var imageUrlFullPath : imageUrls) {
			try {
				if (storeOnLocalDrive) {
					var imageLocationString = resourcesDownloader.ensureExistsLocally(imageUrlFullPath);
					var path = Paths.get(imageLocationString).toString();
					var idStr = Paths.get(path).getFileName().toString();

					if (imageLocationString.startsWith("https://")) {
						URL url = new URL(imageLocationString);
						streams.add(Attachment.fromStream(idStr, url.openStream()));
					} else {
						streams.add(Attachment.fromFile(Paths.get(System.getProperty("user.dir"), path).toString()));
					}
				} else {
					var path = Paths.get(imageUrlFullPath).toString();
					var idStr = Paths.get(path).getFileName().toString();
					var url = new URL(imageUrlFullPath);

					streams.add(Attachment.fromStream(idStr, url.openStream()));
				}
			} catch (IOException e) {
				log.error(e);
			}
		}

		return sendMessage(channel, message, streams);
	}

	private boolean sendMessage(MessageChannel channel, String message, BufferedImage... images) {
		var tuples = new ArrayList<Attachment>();

		int i = 0;
		for (var image : images) {
			try {
				var os = new ByteArrayOutputStream();
				ImageIO.write(image, "png", os);
				var is = new ByteArrayInputStream(os.toByteArray());

				tuples.add(Attachment.builder()
					.name(String.format("%d.png", i))
					.stream(is)
					.build());
			} catch (IOException ex) {
				log.error(ex);
			}
		}

		return sendMessage(channel, message, new ArrayList<>(tuples));
	}

	private boolean sendMessage(MessageChannel channel, String message, List<Attachment> attachments) {
		return sendMessage(channel, message, null, attachments);
	}

	private boolean sendMessage(MessageChannel channel, String message, Snowflake reference, List<Attachment> attachments) {
		var success = true;
		var messageBlocks = Arrays.stream(message.split("\n"))
			.collect(Collectors.toCollection(ArrayList::new));

		for (int i = 0; i < messageBlocks.size(); i++) {
			if (messageBlocks.get(i).length() > 2000) {
				var first = messageBlocks.get(i).substring(0, 1900);
				var second = messageBlocks.get(i).substring(1900, messageBlocks.get(i).length() - 1900);
				messageBlocks.remove(i);
				messageBlocks.add(i, first);
				messageBlocks.add(i + 1, second);
				i--;
				continue;
			}

			if (i + 1 < messageBlocks.size()
				&& String.format("%s\n%s", messageBlocks.get(i), messageBlocks.get(i + 1)).length() < 2000) {
				var first = messageBlocks.remove(i);
				var second = messageBlocks.remove(i);
				messageBlocks.add(i, String.format("%s\n%s", first, second));
				i--;
			}
		}

		for (int i = 0; i < messageBlocks.size(); i++) {
			var messageBlock = messageBlocks.get(i);

			var createMono = channel.createMessage(messageBlock.substring(0, Math.min(messageBlock.length(), 2000)));

			if (i == 0 && reference != null) {
				createMono = createMono.withMessageReference(MessageReferenceData.builder().messageId(reference.asLong()).build());
			}

			if (i == messageBlocks.size() - 1) {
				var streams = new ArrayList<>(attachments);

				createMono = createMono.withFiles(
					streams.stream()
						.map(s -> MessageCreateFields.File.of(s.getName(), s.getStream()))
						.collect(Collectors.toList())
				);
			}

			var msg = createMono
				.retry(5)
				.doOnError(e -> {
					log.error(e);
					accountRepository.findAll().stream()
						.filter(Account::getIsMainAccount)
						.findFirst()
						.ifPresent(account -> sendPrivateMessage(account.getDiscordId(), String.format("# Error when sending a discord message\n```\n%s\n```", e.getMessage())));
				})
				.block();

			attachments.forEach(Attachment::close);

			log.info("sent message to channel with id '{}': messageBlock: '{}'", channel.getId().asLong(), messageBlock);
			success &= msg != null;
		}

		return success;
	}

	private Optional<PrivateChannel> getPrivateChannelForUserInGuild(Long userId, List<Guild> guilds) {
		var allMembersOfAllServers = guilds.stream()
			.map(g -> g.getMembers(EntityRetrievalStrategy.REST).retry(5).collectList().block())
			.filter(Objects::nonNull)
			.flatMap(Collection::stream)
			.collect(Collectors.toList());

		return allMembersOfAllServers.stream()
			.filter(m -> m.getId().asLong() == userId)
			.findFirst()
			.flatMap(member -> getGateway()
				.getUserById(member.getId())
				.retry(5)
				.onErrorResume(e -> Mono.empty())
				.blockOptional()
				.flatMap(u -> u.getPrivateChannel().retry(5).onErrorResume(e -> Mono.empty()).blockOptional()))
			.stream()
			.findFirst();
	}

	public boolean sendPrivateMessage(Long userId, String message) {
		return sendPrivateMessage(userId, message, List.of());
	}

	public boolean sendPrivateMessage(Long userId, String message, List<Attachment> attachments) {
		if (userId == null || getGateway() == null) {
			return false;
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			return getPrivateChannelForUserInGuild(userId, guilds)
				.map(channel -> sendMessage(channel, message, attachments))
				.orElse(false);
		}

		return false;
	}

	private Optional<TextChannel> getServerChannelInGuilds(Long channelId, List<Guild> guilds) {
		return guilds.stream()
			.map(g -> g.getChannels().retry(5).onErrorResume(e -> Mono.empty()).collectList().block())
			.filter(Objects::nonNull)
			.flatMap(Collection::stream)
			.filter(c -> c instanceof TextChannel)
			.map(c -> (TextChannel) c)
			.filter(c -> Objects.equals(c.getId().asLong(), channelId))
			.findFirst();
	}

	public void sendServerMessage(Long channelId, String message, List<Attachment> attachments) {
		if (channelId == null || getGateway() == null) {
			return;
		}

		var guilds = getGateway().getGuilds().collectList().retry(5).onErrorResume(e -> Mono.empty()).block();
		if (guilds != null && !guilds.isEmpty()) {
			getServerChannelInGuilds(channelId, guilds)
				.ifPresent(channel -> sendMessage(channel, message, attachments));
		}
	}

	public void reply(String message, TextChannel channel, Snowflake reference) {
		sendMessage(channel, message, reference, List.of());
	}
}

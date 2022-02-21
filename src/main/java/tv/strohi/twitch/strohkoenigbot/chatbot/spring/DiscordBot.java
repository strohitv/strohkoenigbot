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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ConnectionAccepted;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DiscordBot {
	private final ConfigurationRepository configurationRepository;
	private final List<IChatAction> botActions = new ArrayList<>();

	private GatewayDiscordClient gateway = null;

	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		this.botActions.clear();
		this.botActions.addAll(botActions);
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
			gateway = client.login().block();

			if (gateway != null) {
				gateway.on(MessageCreateEvent.class).subscribe(event -> {
					final Message message = event.getMessage();
					final MessageChannel channel = message.getChannel().block();

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

	public Long loadUserIdFromDiscordServer(String username) {
		if (getGateway() == null) {
			return null;
		}

		Long result = null;

		List<Guild> guilds = getGateway().getGuilds().collectList().block();
		if (guilds != null && guilds.size() > 0) {
			List<Member> members = guilds.get(0).getMembers(EntityRetrievalStrategy.REST).collectList().block();

			if (members != null) {
				result = members.stream()
						.filter(m -> String.format("%s#%s", m.getMemberData().user().username(), m.getMemberData().user().discriminator()).equals(username))
						.map(m -> m.getId().asLong())
						.findFirst()
						.orElse(null);
			}
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

		List<Guild> guilds = getGateway().getGuilds().collectList().block();
		if (guilds != null && guilds.size() > 0) {
			List<GuildChannel> channels = guilds.get(0).getChannels().collectList().block();

			if (channels != null) {
				TextChannel channel = (TextChannel) channels.stream().filter(c -> c.getName().equals(channelName)).findFirst().orElse(null);

				if (channel != null) {
					MessageCreateMono createMono = channel.createMessage(message);

					try {
						List<Tuple<String, InputStream>> streams = new ArrayList<>();


						for (String imageUrl : imageUrls) {
							URL url = new URL(imageUrl);

							String[] segments = url.getPath().split("/");
							String idStr = segments[segments.length - 1];

							streams.add(new Tuple<>(idStr, url.openStream()));
						}

						createMono = createMono.withFiles(
								streams.stream()
										.map(s -> MessageCreateFields.File.of(s.x, s.y))
										.collect(Collectors.toList())
						);
					} catch (IOException e) {
						e.printStackTrace();
					}

					Message msg = createMono.block();
					result = msg != null;
				}
			}
		}

		return result;
	}

	public boolean sendPrivateMessageWithImage(Long userId, String message, String gearUrl, String mainAbilityUrl, String favoredAbilityUrl) {
		if (userId == null || getGateway() == null) {
			return false;
		}

		boolean result = false;

		List<Guild> guilds = getGateway().getGuilds().collectList().block();
		if (guilds != null && guilds.size() > 0) {
			List<Member> members = guilds.get(0).getMembers(EntityRetrievalStrategy.REST).collectList().block();

			if (members != null) {
				PrivateChannel channel = members.stream()
						.filter(m -> m.getId().asLong() == userId)
						.findFirst()
						.flatMap(member -> getGateway()
								.getUserById(member.getId())
								.blockOptional()
								.flatMap(u -> u.getPrivateChannel().blockOptional()))
						.stream()
						.findFirst()
						.orElse(null);

				if (channel != null) {
					MessageCreateMono createMono = channel.createMessage(message);

					try {
						InputStream gearOffer = new URL(gearUrl).openStream();
						InputStream mainAbility = new URL(mainAbilityUrl).openStream();
						InputStream favoredAbility = new URL(favoredAbilityUrl).openStream();
						createMono = createMono.withFiles(
								MessageCreateFields.File.of("gear_offer.png", gearOffer),
								MessageCreateFields.File.of("main_ability.png", mainAbility),
								MessageCreateFields.File.of("favored_ability.png", favoredAbility)
						);
					} catch (IOException e) {
						e.printStackTrace();
					}

					Message msg = createMono.block();
					result = msg != null;
				}
			}
		}

		return result;
	}

	public boolean sendPrivateMessage(Long userId, String message) {
		if (userId == null || getGateway() == null) {
			return false;
		}

		boolean result = false;

		List<Guild> guilds = getGateway().getGuilds().collectList().block();
		if (guilds != null && guilds.size() > 0) {
			List<Member> members = guilds.get(0).getMembers(EntityRetrievalStrategy.REST).collectList().block();

			if (members != null) {
				PrivateChannel channel = members.stream()
						.filter(m -> m.getId().asLong() == userId)
						.findFirst()
						.flatMap(member -> getGateway()
								.getUserById(member.getId())
								.blockOptional()
								.flatMap(u -> u.getPrivateChannel().blockOptional()))
						.stream()
						.findFirst()
						.orElse(null);

				if (channel != null) {
					Message msg = channel.createMessage(message).block();
					result = msg != null;
				}
			}
		}

		return result;
	}

	public void reply(String message, TextChannel channel, Snowflake reference) {
		channel.createMessage(MessageCreateSpec.create().withMessageReference(reference).withContent(message)).block();
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

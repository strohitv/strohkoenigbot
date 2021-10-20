package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateMono;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ConnectionAccepted;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component
public class DiscordBot {
	private final ConfigurationRepository configurationRepository;

	private DiscordClient client = null;
	private GatewayDiscordClient gateway = null;

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
			client = DiscordClient.create(tokens.get(0).getConfigValue());
			gateway = client.login().block();

			if (gateway != null) {
				gateway.on(MessageCreateEvent.class).subscribe(event -> {
					final Message message = event.getMessage();
					final MessageChannel channel = message.getChannel().block();

					if (channel instanceof PrivateChannel && message.getContent().trim().toLowerCase().startsWith("yes")) {
						event.getMessage().getAuthor().ifPresent(author ->
								subscribers.forEach(s -> s.accept(author.getId().asLong()))
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

	public boolean sendPrivateMessageWithImage(long userId, String message, String gearUrl, String mainAbilityUrl, String favoredAbilityUrl) {
		if (getGateway() == null) {
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

	public boolean sendPrivateMessage(long userId, String message) {
		if (getGateway() == null) {
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
}

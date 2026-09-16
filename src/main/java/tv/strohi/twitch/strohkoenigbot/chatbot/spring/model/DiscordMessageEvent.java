package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DiscordMessageEvent extends ApplicationEvent {
	private final MessageCreateEvent event;
	private final MessageChannel channel;
	private final User author;
	private final boolean admin;

	public DiscordMessageEvent(Object source, MessageCreateEvent event, MessageChannel channel, User author, boolean admin) {
		super(source);
		this.event = event;
		this.channel = channel;
		this.author = author;
		this.admin = admin;
	}
}

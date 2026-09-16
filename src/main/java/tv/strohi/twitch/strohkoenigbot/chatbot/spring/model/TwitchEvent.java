package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TwitchEvent extends ApplicationEvent {
	private final Object event;

	public TwitchEvent(Object source, Object event) {
		super(source);
		this.event = event;
	}
}

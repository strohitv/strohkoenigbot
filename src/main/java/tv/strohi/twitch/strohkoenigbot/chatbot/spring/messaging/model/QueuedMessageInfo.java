package tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.spi.StandardLevel;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Attachment;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Target;

import java.util.List;

@Getter
@Setter
@Builder(toBuilder = true)
public class QueuedMessageInfo {
	private StandardLevel level;
	private List<Target> targets;
	private String message;
	private List<Attachment> attachments;
}

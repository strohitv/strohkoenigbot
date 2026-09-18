package tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Attachment;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Target;
import tv.strohi.twitch.strohkoenigbot.data.model.QueuedMessage;
import tv.strohi.twitch.strohkoenigbot.data.repository.QueuedMessageRepository;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.model.QueuedMessageInfo;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class LogQueuer {
	private final QueuedMessageRepository queuedMessageRepository;

	private final ObjectMapper objectMapper;

	public void queue(Logger logger, Level level, List<Target> targets, List<Attachment> attachments, String message, Object... args) {
		message = String.format(message, args);

		logger.log(level, message);

		try {
			queuedMessageRepository.save(QueuedMessage.builder()
				.queuedAt(Instant.now())
				.message(objectMapper.writeValueAsString(QueuedMessageInfo.builder()
					.level(level.getStandardLevel())
					.targets(targets)
					.message(message)
					.attachments(attachments)
					.build()))
				.build());
		} catch (JsonProcessingException e) {
			log.error("Exception while adding a message to the queued messages repository", e);
		}
	}

	public void queue(Logger logger, Level level, @NonNull String message, Object... args) {
		queue(logger, level, List.of(), List.of(), message, args);
	}

	public void infoQueue(Logger logger, @NonNull String message) {
		queue(logger, Level.INFO, message);
	}

	public void infoQueue(Logger logger, @NonNull String message, Object... args) {
		infoQueue(logger, String.format(message, args));
	}

	public void infoQueue(Logger logger, List<Target> targets, List<Attachment> attachments, @NonNull String message, Object... args) {
		queue(logger, Level.INFO, targets, attachments, message, args);
	}

	public void queueLogsAsAttachment(Logger logger, Level level, String message, String attachment) {
		logger.log(level, message);
		logger.log(level, attachment);

		var now = LocalDateTime.now();

		queue(
			logger,
			Level.ERROR,
			List.of(Target.adminTarget()),
			List.of(Attachment.fromStream(
				String.format("attachment-log_%04d-%02d-%02d_%02d-%02d-%02d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond()),
				IOUtils.toInputStream(attachment, StandardCharsets.UTF_8))),
			message);
	}

	public void queueLogsForDebugChannel(Logger logger, String message) {
		queue(logger, Level.INFO,List.of(Target.channel(DiscordChannelDecisionMaker.getDebugChannelName())), List.of(), message);
	}
}

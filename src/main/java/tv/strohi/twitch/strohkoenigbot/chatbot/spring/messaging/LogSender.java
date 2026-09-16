package tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.model.QueuedMessageInfo;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Attachment;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.model.Target;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.QueuedMessageRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class LogSender implements ScheduledService {
	private final DiscordBot discordBot;

	private final ConfigurationRepository configurationRepository;
	private final QueuedMessageRepository queuedMessageRepository;

	private final ObjectMapper objectMapper;

	public boolean areDebugLogsActivated() {
		return "true".equalsIgnoreCase(configurationRepository
			.findByConfigName("LogSender_debugLogs")
			.orElseGet(() ->
				configurationRepository.save(
					Configuration.builder()
						.configName("LogSender_debugLogs")
						.configValue("false")
						.build()
				))
			.getConfigValue());
	}

	public void send(Logger logger, Level level, List<Target> targets, List<Attachment> attachments, String message, Object... args) {
		final var fullMessage = String.format(message, args);

		logger.log(level, fullMessage);

		if (targets.isEmpty()) {
			targets = List.of(Target.adminTarget());
		}

		var sentChannelIds = new ArrayList<Long>();
		for (var target : targets) {
			switch (target.getDiscordTarget()) {
				case PRIVATE_MESSAGE:
					if (target.getId() == null) {
						discordBot.searchUserId(target.getName()).ifPresent(target::setId);
					}

					if (target.getId() != null && !sentChannelIds.contains(target.getId())) {
						discordBot.sendPrivateMessage(target.getId(), fullMessage, attachments);
						sentChannelIds.add(target.getId());
					}
					break;
				case SERVER_CHANNEL:
					var ids = discordBot.searchServerChannelIds(target.getName());

					for (var id : ids) {
						if (!sentChannelIds.contains(id)) {
							discordBot.sendServerMessage(id, fullMessage, attachments);
							sentChannelIds.add(id);
						}
					}
					break;
				case ADMIN:
				default:
					discordBot.getAdminIds().forEach(id -> {
						if (!sentChannelIds.contains(id)) {
							discordBot.sendPrivateMessage(id, fullMessage, attachments);
							sentChannelIds.add(id);
						}
					});
					break;
			}
		}
	}

	public void info(Logger logger, String message) {
		send(logger, Level.INFO, List.of(), List.of(), message);
	}

	public void info(Logger logger, @NonNull String message, Object... args) {
		send(logger, Level.INFO, List.of(), List.of(), message, args);
	}

	public void error(Logger logger, String message) {
		send(logger, Level.ERROR, List.of(), List.of(), message);
	}

	public void error(Logger logger, @NonNull String message, Object... args) {
		send(logger, Level.ERROR, List.of(), List.of(), message, args);
	}

	public void sendOnDebug(Logger logger, String message) {
		if (areDebugLogsActivated()) {
			send(logger, Level.INFO, List.of(), List.of(), message);
		} else {
			logger.debug(message);
		}
	}

	public void sendOnDebug(Logger logger, @NonNull String message, Object... args) {
		sendOnDebug(logger, String.format(message, args));
	}

	public void sendLogsToDebugChannel(Logger logger, String message) {
		logger.info(message);
		discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), message);
	}

	public void sendLogsAsAttachment(Logger logger, Level level, String message, String attachment) {
		logger.log(level, message);
		logger.log(level, attachment);

		var now = LocalDateTime.now();
		discordBot.getAdminIds().forEach(id ->
			discordBot.sendPrivateMessage(
				id,
				message,
				List.of(Attachment.fromStream(
					String.format("attachment-log_%04d-%02d-%02d_%02d-%02d-%02d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond()),
					IOUtils.toInputStream(attachment, StandardCharsets.UTF_8)))));
	}

	private void sendQueuedMessages() {
		var queuedMessages = queuedMessageRepository.findAll();

		for (var queued : queuedMessages) {
			try {
				var messageInfo = objectMapper.readValue(queued.getMessage(), QueuedMessageInfo.class);

				log.info("Sending message which was queued at `{}`", queued.getQueuedAt());
				send(log, Level.forName(messageInfo.getLevel().name(), messageInfo.getLevel().intLevel()), messageInfo.getTargets(), messageInfo.getAttachments(), messageInfo.getMessage());
			} catch (Exception e) {
				log.error("Exception while sending a queued message", e);

				send(
					log,
					Level.ERROR,
					List.of(Target.adminTarget()),
					List.of(Attachment.withDefaultName(String.format("# Exception:\n```\n%s\n```", e))),
					"# Exception while sending a queued message");
			}

			queuedMessageRepository.delete(queued);
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("DiscordBot_sendQueuedServerMessagesWithImageUrls")
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::sendQueuedMessages)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

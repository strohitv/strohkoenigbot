package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LogSender {
	private final DiscordBot discordBot;

	public void sendLogs(Logger logger, String message) {
		logger.info(message);
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, message);
	}

	public void sendLogsAsAttachment(Logger logger, String message, String attachment) {
		logger.info(message);
		logger.info(attachment);

		var now = LocalDateTime.now();
		discordBot.sendPrivateMessageWithAttachment(DiscordBot.ADMIN_ID,
			message,
			String.format("exception-log_%04d-%02d-%02d_%02d-%02d-%02d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond()),
			IOUtils.toInputStream(attachment, StandardCharsets.UTF_8));
	}
}

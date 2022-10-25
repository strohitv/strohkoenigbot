package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;

@Component
@RequiredArgsConstructor
public class LogSender {
	private final DiscordBot discordBot;

	public void sendLogs(Logger logger, String message) {
		logger.debug(message);
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
	}
}

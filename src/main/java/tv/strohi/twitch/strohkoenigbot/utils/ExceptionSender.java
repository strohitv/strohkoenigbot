package tv.strohi.twitch.strohkoenigbot.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;

import java.io.PrintWriter;
import java.io.StringWriter;

@Component
public class ExceptionSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public void send(Exception ex) {
		try {
			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, "Exception occured while refreshing results!!!");
			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, ex.getMessage());

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String stacktrace = pw.toString();

			if (stacktrace.length() >= 2000) {
				stacktrace = stacktrace.substring(0, 1999);
			}

			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, stacktrace);

			ObjectMapper mapper = new ObjectMapper();
			try {
				String serializedEx = mapper.writeValueAsString(ex);

				if (serializedEx.length() >= 2000) {
					serializedEx = serializedEx.substring(0, 1999);
				}

				discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, serializedEx);
			} catch (JsonProcessingException ignored) {
				// ignored
			}
		} catch (Throwable ex2) {
			logger.error(ex2);
		}
	}
}

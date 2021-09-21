package tv.strohi.twitch.strohkoenigbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;

@SpringBootApplication
@EnableScheduling
public class StrohkoenigbotApplication {
	private TwitchChatBot chatBot;

	@Autowired
	public void setChatBot(TwitchChatBot chatBot) {
		this.chatBot = chatBot;

		this.chatBot.initialize();
	}

	public static void main(String[] args) {
		SpringApplication.run(StrohkoenigbotApplication.class, args);
	}
}

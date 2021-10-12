package tv.strohi.twitch.strohkoenigbot;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;

@SpringBootApplication
@EnableScheduling
public class StrohkoenigbotApplication {
	// 1 day - 10 seconds
	private static final long APPLICATION_LIFETIME = 86400000L - 10000;
	private static ConfigurableApplicationContext app = null;

	@Autowired
	public void setChatBot(TwitchChatBot chatBot) {
		chatBot.initialize();
	}

	private HikariDataSource dataSource;

	@Autowired
	public void setDataSource(HikariDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public static void main(String[] args) {
		app = SpringApplication.run(StrohkoenigbotApplication.class, args);
	}

	@Scheduled(fixedRate = APPLICATION_LIFETIME, initialDelay = APPLICATION_LIFETIME)
	public void shutdown() {
		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
		}

		if (app != null) {
			System.exit(SpringApplication.exit(app));
		} else {
			System.exit(0);
		}
	}
}

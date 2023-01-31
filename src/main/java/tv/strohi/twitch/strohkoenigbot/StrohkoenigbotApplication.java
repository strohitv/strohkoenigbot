package tv.strohi.twitch.strohkoenigbot;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
public class StrohkoenigbotApplication {
	private static final Logger logger = LogManager.getLogger(StrohkoenigbotApplication.class.getSimpleName());
	private static List<String> arguments = null;

	private static ConfigurableApplicationContext app = null;

	private HikariDataSource dataSource;

	@Autowired
	public void setDataSource(HikariDataSource dataSource) {
		this.dataSource = dataSource;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
		sendHello();
	}

	public static void main(String[] args) {
		if (Arrays.stream(args).anyMatch(arg -> arg != null && arg.trim().equalsIgnoreCase("local_debug"))) {
			DiscordChannelDecisionMaker.setIsLocalDebug(true);
		}

		arguments = Arrays.stream(args).collect(Collectors.toUnmodifiableList());

		app = SpringApplication.run(StrohkoenigbotApplication.class, args);
	}

	@PreDestroy
	public void onExit() {
		logger.info("stopping application");

		logger.info("sending shutdown message to strohkoenig");
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), "Bot gets shut down!");

		if (dataSource != null && !dataSource.isClosed()) {
			logger.info("closing datasource");
			dataSource.close();
		}
	}

	// let the reboot be done via crontab
//	@Scheduled(cron = "0 43 4 * * *")
	public void shutdown() {
		logger.info("restarting application");

		if (dataSource != null && !dataSource.isClosed()) {
			dataSource.close();
		}

		if (app != null) {
			System.exit(SpringApplication.exit(app));
		} else {
			System.exit(0);
		}
	}

	//	@Scheduled(cron = "0 53 4 * * *")
	public void sendHello() {
		logger.info("sending hello message to strohkoenig");
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), "Bot is started and ready to go!");
	}

	@Bean
	public List<String> getArguments() {
		return arguments;
	}
}

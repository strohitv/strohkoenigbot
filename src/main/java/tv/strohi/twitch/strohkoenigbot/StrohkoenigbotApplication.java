package tv.strohi.twitch.strohkoenigbot;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Duration;
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

	@Autowired
	public void setConfigLocalDebug(ConfigurationRepository configurationRepository) {
		configurationRepository.findByConfigName("debug")
			.ifPresent(debug -> DiscordChannelDecisionMaker.setOrIsLocalDebug("TRUE".equalsIgnoreCase(debug.getConfigValue().trim())));
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
		sendHello();
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	@Bean
	public HttpClient httpClient() {
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(120))
			.version(HttpClient.Version.HTTP_2)
			.build();
	}

	public static void main(String[] args) {
		if (Arrays.stream(args).anyMatch(arg -> arg != null && arg.trim().equalsIgnoreCase("local_debug"))) {
			DiscordChannelDecisionMaker.setIsLocalDebug(true);
		}

		arguments = Arrays.stream(args).collect(Collectors.toUnmodifiableList());

		app = SpringApplication.run(StrohkoenigbotApplication.class, args);
	}

	public static void restart() {
		ApplicationArguments args = app.getBean(ApplicationArguments.class);

		Thread thread = new Thread(() -> {
			app.close();
			app = SpringApplication.run(StrohkoenigbotApplication.class, args.getSourceArgs());
		});

		thread.setDaemon(false);
		thread.start();
	}

	@PreDestroy
	public void onExit() {
		logger.info("stopping application");

		logger.info("sending shutdown message to strohkoenig");
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, "Bot gets shut down!");

		if (dataSource != null && !dataSource.isClosed()) {
			logger.info("closing datasource");
			dataSource.close();
		}
	}

	// let the reboot be done via crontab0
//	@Scheduled(cron = "0 43 4 * * *")

	/**
	 * DAILY REBOOT IS VIA CRONJOB
	 * This method will trigger a shutdown, which will lead to a reboot in combination with the refresh-strohkoenibot.sh script
	 */
	public void shutdown() {
		logger.info("stopping application");

		logger.info("sending message about triggered shutdown to strohkoenig");
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, "Bot will shut itself down!");

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
		discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID, "Bot is started and ready to go!");
	}

	@Bean
	public List<String> getArguments() {
		return arguments;
	}
}

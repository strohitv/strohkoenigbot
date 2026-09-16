package tv.strohi.twitch.strohkoenigbot;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import javax.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
@Log4j2
public class StrohkoenigbotApplication {
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

	private LogSender logSender;

	@Autowired
	public void setLogSender(LogSender logSender) {
		this.logSender = logSender;
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
		log.info("exiting application");

		log.info("sending shutdown message to discord admin");
		logSender.info(log, "Bot gets shut down!");

		if (dataSource != null && !dataSource.isClosed()) {
			log.info("closing datasource");
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
		log.info("stopping application");

		log.info("sending message about triggered shutdown to discord admin");
		logSender.info(log, "Bot will shut itself down!");

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
		log.info("sending hello message to discord admin");
		logSender.info(log, "Bot is started and ready to go!");
	}

	@Bean
	public List<String> getArguments() {
		return arguments;
	}
}

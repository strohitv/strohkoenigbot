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
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableScheduling
public class StrohkoenigbotApplication {
	private static final Logger logger = LogManager.getLogger(StrohkoenigbotApplication.class.getSimpleName());
	private static List<String> arguments = null;

	// 1 day - 10 seconds
	private static final long APPLICATION_LIFETIME = 86400000L - 10000;
	private static ConfigurableApplicationContext app = null;

	private HikariDataSource dataSource;

	@Autowired
	public void setDataSource(HikariDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public static void main(String[] args) {
		arguments = Arrays.stream(args).collect(Collectors.toUnmodifiableList());
		app = SpringApplication.run(StrohkoenigbotApplication.class, args);
	}

//	@Scheduled(fixedRate = APPLICATION_LIFETIME, initialDelay = APPLICATION_LIFETIME)
	@Scheduled(cron = "0 0 18 * * *")
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

	@Bean
	public List<String> getArguments() {
		return arguments;
	}
}

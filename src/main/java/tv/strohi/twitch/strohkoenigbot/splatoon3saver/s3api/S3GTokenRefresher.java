package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ConfigFileConnector;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class S3GTokenRefresher {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;

	private final ConfigFileConnector configFileConnector;
	private final ExceptionLogger exceptionLogger;

	public boolean refreshGToken(Runtime rt, String configFileLocation, String completeCommand) {
		int result = -1;
		int number = 0;
		while (result != 0 && number < 5) {
			try {
				if (number > 0) {
					ConfigFile configFile = configFileConnector.readConfigFile(configFileLocation);
					configFile.setGtoken("");
					configFile.setBullettoken("");
					configFileConnector.storeConfigFile(configFileLocation, configFile);

					try {
						logger.warn("Didn't work, retrying & sleeping for {} seconds before the next attempt", number * 10);
						Thread.sleep(number * 10000);
					} catch (InterruptedException e) {
						logger.error(e);
					}
				}

				number++;
				result = rt.exec("git pull", null, new File(configFileLocation)).waitFor();
				result += rt.exec("pip install -r requirements.txt", null, new File(configFileLocation)).waitFor();

				if (result == 0) {
					ProcessBuilder ps = new ProcessBuilder(completeCommand.split(" "));
					ps.redirectErrorStream(true);
					Process process = ps.start();

					BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));

					StringBuilder sb = new StringBuilder();

					try {
						String line;
						while ((line = in.readLine()) != null) {
							sb.append(line).append("\n");
							logger.info(line);
						}
					} catch (IOException ex) {
						exceptionLogger.logException(logger, ex);
					}

					if (!process.waitFor(10, TimeUnit.MINUTES)) {
						logSender.sendLogs(logger, String.format("Result was %d before the import even started!", result));
						logSender.sendLogsAsAttachment(logger, "s3s command output", sb.toString());

						try {
							process.destroyForcibly();
						} catch (Exception ex) {
							exceptionLogger.logException(logger, ex);
						}
						result = 1; // ERROR
					} // else result is already 0 => success

					in.close();
				} else {
					logSender.sendLogs(logger, String.format("Result was %d before the import even started!", result));
				}
			} catch (IOException | InterruptedException e) {
				logSender.sendLogs(logger, "Exception while executing s3s process, see logs!");
				exceptionLogger.logException(logger, e);
			}
		}

		return result == 0;
	}
}

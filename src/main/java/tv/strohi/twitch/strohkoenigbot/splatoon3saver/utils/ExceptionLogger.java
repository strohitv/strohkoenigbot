package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class ExceptionLogger {
	private final LogSender logSender;

	public void logException(Logger logger, Exception e) {
		var sentExs = new ArrayList<Throwable>();
		Throwable currentEx = e;

		while (!sentExs.contains(currentEx) && currentEx != null) {
			logSender.sendLogs(logger, String.format("**Message**: '%s'", currentEx.getMessage()));

			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			currentEx.printStackTrace(printWriter);

			String stacktrace = stringWriter.toString();
			if (stacktrace.length() > 1900) {
				stacktrace = stacktrace.substring(0, 1900);
			}

			logSender.sendLogs(logger, String.format("**Stacktrace**: ```\n%s\n```", stacktrace));

			sentExs.add(currentEx);
			currentEx = currentEx.getCause();
		}
	}
}

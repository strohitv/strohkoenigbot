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
		logExceptionAsAttachment(logger, "An Exception has occurred!", e);
	}

	public void logExceptionAsAttachment(Logger logger, String title, Exception e) {
		var sentExs = new ArrayList<Throwable>();
		Throwable currentEx = e;

		var messageBuilder = new StringBuilder();
		int exceptionNumber = 1;

		while (!sentExs.contains(currentEx) && currentEx != null) {
			messageBuilder.append("# Exception #").append(exceptionNumber).append("\n\n");
			exceptionNumber++;

			messageBuilder.append("### Message\n- ").append(currentEx.getMessage()).append("\n\n");

			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			currentEx.printStackTrace(printWriter);

			String stacktrace = stringWriter.toString();
			messageBuilder.append("### Stacktrace\n```\n").append(stacktrace).append("\n```\n\n");

			sentExs.add(currentEx);
			currentEx = currentEx.getCause();
		}

		var wholeMessage = messageBuilder.toString();
		logSender.sendLogsAsAttachment(logger, String.format("## Error\n%s\n### Exception", title), wholeMessage);
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import lombok.extern.log4j.Log4j2;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

@Log4j2
public abstract class ChatAction implements IChatAction {
	private static ExceptionLogger exceptionLogger;

	public static void setExceptionLogger(ExceptionLogger exceptionLogger) {
		ChatAction.exceptionLogger = exceptionLogger;
	}

	protected abstract void execute(ActionArgs args);

	@Override
	public final void run(ActionArgs args) {
		try {
			execute(args);
		} catch (Exception ex) {
			if (exceptionLogger != null) {
				exceptionLogger.logExceptionAsAttachment(log, "Exception occurred during chat action execution", ex);
			} else {
				log.error(ex);
			}
		}
	}
}

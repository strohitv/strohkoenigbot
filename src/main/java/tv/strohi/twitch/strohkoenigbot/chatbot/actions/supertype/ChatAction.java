package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class ChatAction implements IChatAction {
	protected abstract void execute(ActionArgs args);

	@Override
	public final void run(ActionArgs args) {
		try {
			execute(args);
		} catch (Exception ex) {
			log.error(ex);
		}
	}
}

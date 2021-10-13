package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

public abstract class ChatAction implements IChatAction {
	protected abstract void execute(ActionArgs args);

	@Override
	public final void run(ActionArgs args) {
		try {
			execute(args);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.junit.jupiter.api.Test;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;

import static org.junit.jupiter.api.Assertions.*;

class ManageSplatnetNotificationsActionTest {
	@Test
	void run() {
		ActionArgs args = new ActionArgs();
		args.getArguments().put(ArgumentKey.Message, "!notify ungültig any head test stealth jump");

		new ManageSplatnetNotificationsAction().run(args);

		args.getArguments().put(ArgumentKey.Message, "!notify ungültig any shoes test stealth jump");
		new ManageSplatnetNotificationsAction().run(args);
	}
}

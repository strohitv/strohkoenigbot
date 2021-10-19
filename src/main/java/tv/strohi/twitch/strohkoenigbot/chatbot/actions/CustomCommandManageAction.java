package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;

import java.util.EnumSet;

@Component
public class CustomCommandManageAction extends ChatAction {
//	private CommandRepository repository;
//
//	@Autowired
//	public void setRepository(CommandRepository repository) {
//		this.repository = repository;
//	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage);
	}

	@Override
	public void execute(ActionArgs args) {
//		List<Command> commands = repository.findAll(Sort.unsorted());
//
//		System.out.println(commands);
	}
}

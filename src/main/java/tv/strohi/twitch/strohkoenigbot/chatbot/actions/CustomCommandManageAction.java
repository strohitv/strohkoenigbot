package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.data.repository.CommandRepository;

import java.util.EnumSet;

@Component
public class CustomCommandManageAction extends ChatAction {
	private CommandRepository repository;

	@Autowired
	public void setRepository(CommandRepository repository) {
		this.repository = repository;
	}

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

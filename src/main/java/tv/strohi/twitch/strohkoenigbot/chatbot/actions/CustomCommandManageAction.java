package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.data.model.Command;
import tv.strohi.twitch.strohkoenigbot.data.repository.CommandRepository;

import java.util.EnumSet;
import java.util.List;

@Component
public class CustomCommandManageAction implements IChatAction {
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
	public void run(ActionArgs args) {
		List<Command> commands = repository.findAll(Sort.unsorted());

		System.out.println(commands);
	}
}

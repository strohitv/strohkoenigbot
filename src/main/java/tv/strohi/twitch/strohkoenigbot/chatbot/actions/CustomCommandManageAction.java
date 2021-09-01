package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.data.model.Command;
import tv.strohi.twitch.strohkoenigbot.data.repository.CommandRepository;

import java.util.List;

@Component
public class CustomCommandManageAction extends ChatAction {
	private CommandRepository repository;

	@Autowired
	public void setRepository(CommandRepository repository) {
		this.repository = repository;
	}

	@Override
	public void run() {
		List<Command> commands = repository.findAll(Sort.unsorted());

		System.out.println(commands);
	}
}

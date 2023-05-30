package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.obs.ObsController;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class ToggleObsHtmlSourcesAction extends ChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage);
	}

	private final ObsController obsController;

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!badges") || message.startsWith("!abzeichen")) {
			boolean enable = !message.contains("off") && !message.contains("aus");

			logger.info("toggle badges visibility action was called");

			if (enable) {
				// prevent overlapping
				disableSources();
			}

			obsController.changeSourceEnabled("Badges", enable, result -> {
				if (result) {
					if (enable) {
						if ("stroh_ohne_i".equals(args.getArguments().get(ArgumentKey.ChannelName))) {
							args.getReplySender().send("Die Abzeichen werden nun im Stream angezeigt. Vergiss nicht, sie mit \"!abzeichen aus\" wieder zu verstecken, sobald du dich entschieden hast!");
						} else {
							args.getReplySender().send("Badges are now visible in stream. Don't forget to hide them again using \"!badges off\" as soon as you selected up to three!");
						}
					}
				}
			});
		} else if (message.startsWith("!emotes") || message.startsWith("!posen")) {
			boolean enable = !message.contains("off") && !message.contains("aus");

			logger.info("toggle badges visibility action was called");

			if (enable) {
				// prevent overlapping
				disableSources();
			}

			obsController.changeSourceEnabled("Emotes", enable, result -> {
				if (result) {
					if (enable) {
						if ("stroh_ohne_i".equals(args.getArguments().get(ArgumentKey.ChannelName))) {
							args.getReplySender().send("Die Siegesposen werden nun im Stream angezeigt. Vergiss nicht, sie mit \"!posen aus\" wieder zu verstecken, sobald du dich entschieden hast!");
						} else {
							args.getReplySender().send("Emotes are now visible in stream. Don't forget to hide them again using \"!emotes off\" as soon as you've made a choice!");
						}
					}
				}
			});
		}
	}

	private void disableSources() {
		obsController.changeSourceEnabled("Badges", false, result -> {
		});
		obsController.changeSourceEnabled("Emotes", false, result -> {
		});
	}
}

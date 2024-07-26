package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.S3StatsSenderUtils;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class S3PlayerStatsAction extends ChatAction {
	private final S3Downloader s3Downloader;
	private final S3StatsSenderUtils senderUtils;

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!player")) {
			var commandArg = message.replace("!player", "").trim();
			if (!commandArg.isBlank() && commandArg.matches("^\\d+$")) {
				s3Downloader.downloadBattles(true);

				var playerId = Long.parseLong(commandArg);
				senderUtils.respondWithPlayerStats(args, playerId);
			} else {
				args.getReplySender().send("**ERROR**: please provide a player id to this command.");
			}
		}
	}
}

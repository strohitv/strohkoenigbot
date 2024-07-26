package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.S3StatsSenderUtils;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class S3GameStatsAction extends ChatAction {
	private final Splatoon3VsResultRepository vsResultRepository;
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

		if (message.startsWith("!game")) {
			var matchDecidingNumber = 0L;

			var commandArg = message.replace("!game", "").trim();
			if (!commandArg.isBlank() && commandArg.matches("^-?\\d+$")) {
				matchDecidingNumber = Long.parseLong(commandArg);
			}

			s3Downloader.downloadBattles(true);
			senderUtils.respondWithGameStats(args, matchDecidingNumber);
		}
	}
}

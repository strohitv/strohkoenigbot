package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;

import java.util.EnumSet;
import java.util.List;

@Component
public class BadgeAction implements IChatAction {
	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	public void run(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!badges")) {
			List<SplatoonWeapon> weapons = weaponRepository.findByTurfLessThan(100_000);

			long leftToPaint = weapons.stream().map(w -> 100_000 - w.getTurf()).reduce(0L, Long::sum);
			double daysUntilGoalReached = leftToPaint / 40_000.0;

			TwitchDiscordMessageSender sender = args.getReplySender();

			String reply = "I still need to paint a total of **%d** points on **%d** different weapons. That's **%.2f days** if I paint **40k points** every day.";
			if (args.getReason() == TriggerReason.ChatMessage) {
				reply = reply.replace("**", "");
			}

			sender.send(String.format(reply, leftToPaint, weapons.size(), daysUntilGoalReached));
		}
	}
}

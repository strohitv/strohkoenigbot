package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.*;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

@Component
public class BadgeAction extends ChatAction {
	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private SplatoonMatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(SplatoonMatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	public void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!badges")) {
			List<SplatoonWeapon> weapons = weaponRepository.findByTurfLessThan(100_000);

			long leftToPaint = weapons.stream().map(w -> 100_000 - w.getTurf()).reduce(0L, Long::sum);
			double daysUntilGoalReached = leftToPaint / 40_000.0;

			String reply = "I still need to paint a total of **%d** points on **%d** different weapons. That's **%.2f days** if I paint **40k points** every day.";
			if (args.getReason() == TriggerReason.ChatMessage) {
				reply = reply.replace("**", "");
			}

			args.getReplySender().send(String.format(reply, leftToPaint, weapons.size(), daysUntilGoalReached));
		} else if (message.startsWith("!paint")) {
			Calendar c = new GregorianCalendar();
			c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			long startTime = c.toInstant().getEpochSecond(); //the midnight, that's the first second of the day.

			List<SplatoonMatch> matches = matchRepository.findByStartTimeGreaterThanEqual(startTime);

			long todayPaint = matches.stream().map(m -> (long)m.getTurfGain()).reduce(0L, Long::sum);
			long weaponCount = matches.stream().map(SplatoonMatch::getWeaponId).distinct().count();

			String reply = "Today, I painted a total of **%d points** on **%d** different weapons.";
			if (args.getReason() == TriggerReason.ChatMessage) {
				reply = reply.replace("**", "");
			}

			args.getReplySender().send(String.format(reply, todayPaint, weaponCount));
		}
	}
}

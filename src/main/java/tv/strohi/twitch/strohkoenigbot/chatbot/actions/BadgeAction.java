package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.DailyStatsSender;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

@Component
public class BadgeAction implements IChatAction {
	private Splatoon2WeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(Splatoon2WeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private Splatoon2MatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(Splatoon2MatchRepository matchRepository) {
		this.matchRepository = matchRepository;
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
			List<Splatoon2Weapon> weapons = weaponRepository.findByTurfLessThan(100_000);

			long leftToPaint = weapons.stream().map(w -> 100_000 - w.getTurf()).reduce(0L, Long::sum);
			double daysUntilGoalReached = leftToPaint / 40_000.0;
			double dailyPaintUntilS3 = DailyStatsSender.getDailyPaintUntilSplatoon3(leftToPaint);

			String reply = "I still need to paint a total of **%d** points on **%d** different weapons. That's **%.2f days** if I paint **40k points** every day (or **%.2f** paint per day until 9/9).";
			if (args.getReason() == TriggerReason.ChatMessage) {
				reply = reply.replace("**", "");
			}

			args.getReplySender().send(String.format(reply, leftToPaint, weapons.size(), daysUntilGoalReached, dailyPaintUntilS3));
		} else if (message.startsWith("!paint")) {
			Calendar c = new GregorianCalendar();
			c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			long startTime = c.toInstant().getEpochSecond(); //the midnight, that's the first second of the day.

			List<Splatoon2Match> matches = matchRepository.findByStartTimeGreaterThanEqual(startTime);

			long todayPaint = matches.stream().map(m -> (long)m.getTurfGain()).reduce(0L, Long::sum);
			long weaponCount = matches.stream().map(Splatoon2Match::getWeaponId).distinct().count();

			String reply = "Today, I painted a total of **%d points** on **%d** different weapons.";
			if (args.getReason() == TriggerReason.ChatMessage) {
				reply = reply.replace("**", "");
			}

			args.getReplySender().send(String.format(reply, todayPaint, weaponCount));
		}
	}
}

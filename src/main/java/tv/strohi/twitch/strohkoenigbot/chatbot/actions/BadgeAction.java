package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.*;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.DailyStatsSender;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

@Component
public class BadgeAction extends ChatAction {
	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
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
	public void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!s2 badges")) {
			Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(new Account());

			List<Splatoon2WeaponStats> weapons = weaponStatsRepository.findByTurfLessThanAndAccountId(100_000, account.getId());

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

			Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(new Account());

			List<Splatoon2Match> matches = matchRepository.findByAccountIdAndStartTimeGreaterThanEqual(account.getId(), startTime);

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

package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class DailyStatsSender {
	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

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

	@Scheduled(cron = "0 25 0 * * *")
	public void sendDailyStatsToDiscord() {
		Calendar c = new GregorianCalendar();
		c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		long endTime = c.toInstant().getEpochSecond();
		c.add(Calendar.DAY_OF_YEAR, -1);
		long startTime = c.toInstant().getEpochSecond(); //the midnight, that's the first second of the day.

		List<SplatoonMatch> matches = matchRepository.findByStartTimeGreaterThanEqualAndEndTimeLessThanEqual(startTime, endTime);
		List<SplatoonWeapon> weapons = weaponRepository.findByTurfLessThan(100_000);

		long yesterdayPaint = matches.stream().map(m -> (long)m.getTurfGain()).reduce(0L, Long::sum);
		long weaponCount = matches.stream().map(SplatoonMatch::getWeaponId).distinct().count();

		List<SplatoonWeapon> newRedBadgeWeapons = matches.stream()
				.filter(m -> m.getTurfTotal() >= 100_000 && m.getTurfTotal() - m.getTurfGain() < 100_000)
				.map(m -> weaponRepository.findById(m.getWeaponId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		long leftToPaint = weapons.stream().map(w -> 100_000 - w.getTurf()).reduce(0L, Long::sum);
		double daysUntilGoalReached = leftToPaint / 40_000.0;

		String message = String.format("Yesterday, I painted a total sum of **%d** points on **%d** different weapons.\n\nI still need to paint a total of **%d** points on **%d** different weapons.\nThat's **%.2f days** if I paint **40k points** every day.", yesterdayPaint, weaponCount, leftToPaint, weapons.size(), daysUntilGoalReached);

		if (newRedBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nThese **").append(newRedBadgeWeapons.size()).append("** weapons got their red badge yesterday:");

			for (SplatoonWeapon weapon : newRedBadgeWeapons) {
			    builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			message = builder.toString();
		}

		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
	}
}
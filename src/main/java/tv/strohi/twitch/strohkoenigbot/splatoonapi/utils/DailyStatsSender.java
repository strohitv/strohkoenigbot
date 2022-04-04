package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponClass;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponKit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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

	@Scheduled(cron = "0 10 0 * * *")
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

		if (matches.size() > 0) {
			String weaponStats = createWeaponStatsCsv(matches);

			Date yesterday = c.getTime();
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String strDate = dateFormat.format(yesterday);

			discordBot.sendPrivateMessageWithAttachment(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
					message,
					String.format("%s.csv", strDate),
					new ByteArrayInputStream(weaponStats.getBytes(StandardCharsets.UTF_8)));
		} else {
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
		}
	}

	private String createWeaponStatsCsv(List<SplatoonMatch> yesterdayMatches) {
		StringBuilder builder = new StringBuilder("Name;Class;Sub;Special;Total Paint;Paint Left;Painted Yesterday;Matches;Wins;Defeats;Win rate;Wins delta;Defeats delta;Paint per Match");

		List<SplatoonWeapon> allWeapons = weaponRepository.findAll().stream()
				.sorted((x, y) -> y.getTurf().compareTo(x.getTurf()))
				.collect(Collectors.toList());

		for (SplatoonWeapon weapon : allWeapons) {
			long yesterdayPaint = yesterdayMatches.stream()
					.filter(w -> w.getWeaponId() == weapon.getId())
					.map(m -> (long)m.getTurfGain())
					.reduce(0L, Long::sum);
			long yesterdayWins = yesterdayMatches.stream()
					.filter(w -> w.getWeaponId() == weapon.getId() && w.getMatchResult() == SplatoonMatchResult.Win)
					.count();
			long yesterdayDefeats = yesterdayMatches.stream()
					.filter(w -> w.getWeaponId() == weapon.getId() && w.getMatchResult() != SplatoonMatchResult.Win)
					.count();

			WeaponKit weaponKit = WeaponKit.All.stream().filter(wk -> wk.getName().equalsIgnoreCase(weapon.getName())).findFirst().orElse(null);
			WeaponClass weaponClass = WeaponClass.Shooter;
			if (weaponKit != null) {
				weaponClass = weaponKit.getWeaponClass();
			}

			builder.append("\n")
					.append(weapon.getName()).append(";")
					.append(weaponClass.getName()).append(";")
					.append(weapon.getSubName()).append(";")
					.append(weapon.getSpecialName()).append(";")
					.append(weapon.getTurf()).append(";")
					.append(100_000 - weapon.getTurf() > 0 ? 100_000 - weapon.getTurf() : 0).append(";")
					.append(yesterdayPaint).append(";")
					.append(getNumber(weapon.getWins()) + getNumber(weapon.getDefeats())).append(";")
					.append(getNumber(weapon.getWins())).append(";")
					.append(getNumber(weapon.getDefeats())).append(";")
					.append(String.format("%.2f", getNumber(weapon.getWins()) * 100 / ((double)(getNumber(weapon.getWins()) + getNumber(weapon.getDefeats()))))).append(";")
					.append(yesterdayWins).append(";")
					.append(yesterdayDefeats).append(";")
					.append(String.format("%.2f", calculateAvgPaint(weapon.getTurf(), getNumber(weapon.getWins()) + getNumber(weapon.getDefeats()))));
		}

		return builder.toString();
	}

	private int getNumber(Integer number) {
		return number != null ? number : 0;
	}

	private double calculateAvgPaint(Long turf, Integer matchCount) {
		if (matchCount == null || matchCount < 1) {
			matchCount = 1;
		}

		return turf / (double) matchCount;
	}
}

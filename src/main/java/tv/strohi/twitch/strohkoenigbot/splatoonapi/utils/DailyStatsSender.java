package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponClass;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponKit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@Component
public class DailyStatsSender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2WeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(Splatoon2WeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
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

	@Scheduled(cron = "0 10 0 * * *")
//	@Scheduled(cron = "0 * * * * *")
	public void sendDailyStatsToDiscord() {
		Calendar c = new GregorianCalendar();
		c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		long endTime = c.toInstant().getEpochSecond();
		c.add(Calendar.DAY_OF_YEAR, -1);
		long startTime = c.toInstant().getEpochSecond(); //the midnight, that's the first second of the day.

		Account account = accountRepository.findAll().stream()
				.filter(Account::getIsMainAccount)
				.findFirst()
				.orElse(new Account());

		List<Splatoon2Match> matches = matchRepository.findByAccountIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(account.getId(), startTime, endTime);
		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), String.format("found %d matches..", matches.size()));

		List<Splatoon2WeaponStats> weaponStats = weaponStatsRepository.findByTurfLessThanAndAccountId(100_000, account.getId());

		long yesterdayPaint = matches.stream().map(m -> (long) m.getTurfGain()).reduce(0L, Long::sum);
		long weaponCount = matches.stream().map(Splatoon2Match::getWeaponId).distinct().count();

		List<Splatoon2WeaponStats> newRedBadgeWeaponStats = matches.stream()
				.filter(m -> m.getTurfTotal() >= 100_000 && m.getTurfTotal() - m.getTurfGain() < 100_000)
				.map(m -> weaponStatsRepository.findByWeaponIdAndAccountId(m.getWeaponId(), account.getId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Splatoon2Weapon> newRedBadgeWeapons = newRedBadgeWeaponStats.stream()
				.map(ws -> weaponRepository.findById(ws.getWeaponId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		long leftToPaint = weaponStats.stream().map(w -> 100_000 - w.getTurf()).reduce(0L, Long::sum);
		double daysUntilGoalReached = leftToPaint / 40_000.0;

		double dailyPaintUntilS3 = getDailyPaintUntilSplatoon3(leftToPaint);

		String message = String.format("Yesterday, I painted a total sum of **%d** points on **%d** different weapons.\n\nI still need to paint a total of **%d** points on **%d** different weapons.\nThat's **%.2f days** if I paint **40k points** every day (or **%.2f** paint per day until 9/9).", yesterdayPaint, weaponCount, leftToPaint, weaponStats.size(), daysUntilGoalReached, dailyPaintUntilS3);

		if (newRedBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nThese **").append(newRedBadgeWeapons.size()).append("** weapons got their red badge yesterday:");

			for (Splatoon2Weapon weapon : newRedBadgeWeapons) {
				builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			message = builder.toString();
		}

		if (matches.size() > 0) {
			String weaponStatsCsv = createWeaponStatsCsv(account.getId(), matches);

			Date yesterday = c.getTime();
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String strDate = dateFormat.format(yesterday);

			discordBot.sendPrivateMessageWithAttachment(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
					message,
					String.format("%s.csv", strDate),
					new ByteArrayInputStream(weaponStatsCsv.getBytes(StandardCharsets.UTF_8)));
		} else {
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), message);
		}
	}

	public static double getDailyPaintUntilSplatoon3(long leftToPaint) {
		long daysUntilS3 = Instant.now().until(Instant.parse("2022-09-09T00:00:00.00Z"), DAYS);
		if (daysUntilS3 < 1) {
			daysUntilS3 = 1;
		}

		return leftToPaint / (double) daysUntilS3;
	}

	private String createWeaponStatsCsv(long accountId, List<Splatoon2Match> yesterdayMatches) {
		StringBuilder builder = new StringBuilder("Name;Class;Sub;Special;Total Paint;Paint Left;Painted Yesterday;Matches;Wins;Defeats;Win rate;Wins delta;Defeats delta;Paint per Match;Current Flag"
//				+ ";Current Flag delta"
				+ ";Max Flag"
//				+ ";Max Flag delta"
		);

		// TODO: current and max flag delta by storing weapon stats with date

		List<Splatoon2Weapon> allWeapons = new ArrayList<>(weaponRepository.findAll());

		List<Splatoon2WeaponStats> allWeaponStats = weaponStatsRepository.findByAccountId(accountId).stream()
				.sorted((x, y) -> y.getTurf().compareTo(x.getTurf()))
				.collect(Collectors.toList());

		discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), String.format("Found %d weapons..", allWeaponStats.size()));

		boolean sendAllWeapons = false;

		for (Splatoon2WeaponStats weaponStats : allWeaponStats) {
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), String.format("Next weapon: **%d**..", weaponStats.getWeaponId()));

			List<Splatoon2Match> yesterdayMatchesForWeapon = yesterdayMatches.stream()
					.filter(m -> m.getWeaponId().equals(weaponStats.getWeaponId()))
					.collect(Collectors.toList());

			long yesterdayPaint = yesterdayMatchesForWeapon.stream()
					.map(m -> (long) m.getTurfGain())
					.reduce(0L, Long::sum);
			long yesterdayWins = yesterdayMatches.stream()
					.filter(m -> m.getWeaponId().equals(weaponStats.getWeaponId()) && m.getMatchResult() == Splatoon2MatchResult.Win)
					.count();
			long yesterdayDefeats = yesterdayMatches.stream()
					.filter(m -> m.getWeaponId().equals(weaponStats.getWeaponId()) && m.getMatchResult() != Splatoon2MatchResult.Win)
					.count();

			if (weaponStats.getCurrentFlag() == null) {
				weaponStats.setCurrentFlag(0.0);
			}

			if (weaponStats.getMaxFlag() == null) {
				weaponStats.setMaxFlag(0.0);
			}

			double currentFlag = yesterdayMatchesForWeapon.stream()
					.map(Splatoon2Match::getCurrentFlag)
					.reduce((first, second) -> second)
					.orElse(weaponStats.getCurrentFlag());

			double maxFlag = yesterdayMatchesForWeapon.stream()
					.map(Splatoon2Match::getCurrentFlag)
					.max(Comparator.naturalOrder())
					.orElse(weaponStats.getMaxFlag());

//			double currentFlagDelta = yesterdayMatchesForWeapon.stream()
//					.map(Splatoon2Match::getCurrentFlag)
//					.reduce((a, b) -> a - b)
//					.orElse(0.0);
//			double maxFlagDelta = weaponStats.getMaxFlag();

			Splatoon2Weapon weapon = allWeapons.stream().filter(w -> w.getId() == weaponStats.getWeaponId()).findFirst().orElse(new Splatoon2Weapon());

			WeaponKit weaponKit = WeaponKit.All.stream().filter(wk -> wk.getName().equalsIgnoreCase(weapon.getName().trim())).findFirst().orElse(null);
			WeaponClass weaponClass = WeaponClass.Shooter;
			if (weaponKit != null) {
				weaponClass = weaponKit.getWeaponClass();
			} else {
				sendAllWeapons = true;
				ObjectMapper mapper = new ObjectMapper();

				try {
					discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
							String.format("weapon.getName(): %s, weapon: %s",
									weapon.getName(), mapper.writeValueAsString(weaponStats)));
				} catch (Exception ex) {
					logger.error(ex);
				}
			}

			builder.append("\n")
					.append(weapon.getName())
					.append(";").append(weaponClass.getName())
					.append(";").append(weapon.getSubName())
					.append(";").append(weapon.getSpecialName())
					.append(";").append(weaponStats.getTurf())
					.append(";").append(100_000 - weaponStats.getTurf() > 0 ? 100_000 - weaponStats.getTurf() : 0)
					.append(";").append(yesterdayPaint)
					.append(";").append(getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))
					.append(";").append(getNumber(weaponStats.getWins()))
					.append(";").append(getNumber(weaponStats.getDefeats()))
					.append(";").append(String.format("%d%%", getNumber(weaponStats.getWins()) * 100 / (getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))))
					.append(";").append(yesterdayWins)
					.append(";").append(yesterdayDefeats)
					.append(";").append(String.format("%.2f", calculateAvgPaint(weaponStats.getTurf(), getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))))
					.append(";").append(String.format("%.2f", currentFlag))
//					.append(";").append(String.format("%.2f", currentFlag - currentFlagDelta))
					.append(";").append(String.format("%.2f", maxFlag))
//					.append(";").append(String.format("%.2f", (maxFlag > maxFlagDelta) ? maxFlag - maxFlagDelta : 0.0))
			;
		}

		if (sendAllWeapons) {
			StringBuilder weaponNameBuilder = new StringBuilder();
			for (WeaponKit kit : WeaponKit.All) {
				if (weaponNameBuilder.length() + kit.getName().length() + 1 >= 2000) {
					discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), weaponNameBuilder.toString().trim());
					weaponNameBuilder = new StringBuilder();
				}

				weaponNameBuilder.append(kit.getName()).append("\n");
			}

			if (weaponNameBuilder.length() > 0) {
				discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"), weaponNameBuilder.toString().trim());
			}
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

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static tv.strohi.twitch.strohkoenigbot.utils.TimezoneUtils.timeOfTimezoneIsBetweenTimes;

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

	@Scheduled(cron = "0 15 * * * *")
//	@Scheduled(cron = "0 15 0 * * *")
//	@Scheduled(cron = "0 * * * * *")
	public void sendDailyStatsToDiscord() {
		List<Account> accounts = accountRepository.findAll().stream()
				.filter(a -> a.getShouldSendDailyStats() != null && a.getShouldSendDailyStats())
				.filter(a -> a.getSplatoonCookie() != null && !a.getSplatoonCookie().isBlank())
				.filter(a -> a.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(a.getSplatoonCookieExpiresAt()))
				.filter(a -> a.getTimezone() != null && !a.getTimezone().isBlank())
				.filter(a -> timeOfTimezoneIsBetweenTimes(a.getTimezone(), 0, 10, 0, 20))
				.collect(Collectors.toList());

		for (Account account : accounts) {
			sendDailyStatsAsPrivateMessage(account);
		}
	}

	public void sendDailyStatsToAccount(long accountId) {
		accountRepository.findAll().stream()
				.filter(a -> a.getId() == accountId)
				.filter(a -> a.getDiscordId() != null)
				.filter(a -> a.getShouldSendDailyStats() != null && a.getShouldSendDailyStats())
				.filter(a -> a.getSplatoonCookie() != null && !a.getSplatoonCookie().isBlank())
				.filter(a -> a.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(a.getSplatoonCookieExpiresAt()))
				.findFirst().ifPresent(this::sendDailyStatsAsPrivateMessage);
	}

	private void sendDailyStatsAsPrivateMessage(Account account) {
		ZonedDateTime time = Instant.now().atZone(ZoneId.of(account.getTimezone())).truncatedTo(DAYS);
		long endTime = time.toInstant().getEpochSecond();
		long startTime = time.minus(1, DAYS).toInstant().getEpochSecond(); //the midnight, that's the first second of the day.

		List<Splatoon2Match> matches = matchRepository.findByAccountIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(account.getId(), startTime, endTime);
		logger.info("found {} matches..", matches.size());

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

		List<Splatoon2WeaponStats> newBronzeBadgeWeaponStats = matches.stream()
				.filter(m -> m.getTurfTotal() >= 500_000 && m.getTurfTotal() - m.getTurfGain() < 500_000)
				.map(m -> weaponStatsRepository.findByWeaponIdAndAccountId(m.getWeaponId(), account.getId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Splatoon2Weapon> newBronzeBadgeWeapons = newBronzeBadgeWeaponStats.stream()
				.map(ws -> weaponRepository.findById(ws.getWeaponId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		List<Splatoon2WeaponStats> newSilverBadgeWeaponStats = matches.stream()
				.filter(m -> m.getTurfTotal() >= 1_000_000 && m.getTurfTotal() - m.getTurfGain() < 1_000_000)
				.map(m -> weaponStatsRepository.findByWeaponIdAndAccountId(m.getWeaponId(), account.getId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Splatoon2Weapon> newSilverBadgeWeapons = newSilverBadgeWeaponStats.stream()
				.map(ws -> weaponRepository.findById(ws.getWeaponId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		List<Splatoon2WeaponStats> newGoldBadgeWeaponStats = matches.stream()
				.filter(m -> m.getTurfTotal() >= 9_999_999 && m.getTurfTotal() - m.getTurfGain() < 9_999_999)
				.map(m -> weaponStatsRepository.findByWeaponIdAndAccountId(m.getWeaponId(), account.getId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Splatoon2Weapon> newGoldBadgeWeapons = newGoldBadgeWeaponStats.stream()
				.map(ws -> weaponRepository.findById(ws.getWeaponId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		String message = String.format("Yesterday, you painted a total sum of **%d** points on **%d** different weapons in **%d** matches (**%d** wins, **%d** defeats).",
				yesterdayPaint, weaponCount, matches.size(), (int) matches.stream().filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win).count(),
				(int) matches.stream().filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win).count());

		if (newRedBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nYou received a red badge on these **").append(newRedBadgeWeapons.size()).append("** weapons yesterday:");

			for (Splatoon2Weapon weapon : newRedBadgeWeapons) {
				builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			message = builder.toString();
		}

		if (newBronzeBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nYou received a bronze badge on these **").append(newBronzeBadgeWeapons.size()).append("** weapons yesterday:");

			for (Splatoon2Weapon weapon : newBronzeBadgeWeapons) {
				builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			message = builder.toString();
		}

		if (newSilverBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nYou received a silver badge on these **").append(newSilverBadgeWeapons.size()).append("** weapons yesterday:");

			for (Splatoon2Weapon weapon : newSilverBadgeWeapons) {
				builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			message = builder.toString();
		}

		if (newGoldBadgeWeapons.size() > 0) {
			StringBuilder builder = new StringBuilder(message);
			builder.append("\n\nYou received a gold badge on these **").append(newGoldBadgeWeapons.size()).append("** weapons yesterday:");

			for (Splatoon2Weapon weapon : newGoldBadgeWeapons) {
				builder.append("\n- **").append(weapon.getName()).append("** (").append(weapon.getSubName()).append(", ").append(weapon.getSpecialName()).append(")");
			}

			builder.append("\nCongratulations!");

			message = builder.toString();
		}

		if (matches.size() > 0) {
			String weaponStatsCsv = createWeaponStatsCsv(account.getId(), matches);

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
					.withZone(ZoneId.of(account.getTimezone()));
			String strDate = formatter.format(time.minus(1, DAYS).toInstant());

			discordBot.sendPrivateMessageWithAttachment(account.getDiscordId(),
					message,
					String.format("%s.csv", strDate),
					new ByteArrayInputStream(weaponStatsCsv.getBytes(StandardCharsets.UTF_8)));
		} else {
			message = String.format("%s\nYou won't receive a CSV today as you didn't play online and nothing has changed since the last time you received a CSV.", message);
			discordBot.sendPrivateMessage(account.getDiscordId(), message);
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
		StringBuilder builder = new StringBuilder("Name;Class;Sub;Special;Total Paint;Painted Yesterday;Paint per Match;Matches;Wins;Defeats;Win rate;Wins Yesterday;Defeats Yesterday;Current Flag"
//				+ ";Current Flag delta"
				+ ";Max Flag"
//				+ ";Max Flag delta"
		);

		// TODO: current and max flag delta by storing weapon stats with date

		List<Splatoon2Weapon> allWeapons = new ArrayList<>(weaponRepository.findAll());

		List<Splatoon2WeaponStats> allWeaponStats = weaponStatsRepository.findByAccountId(accountId).stream()
				.sorted((x, y) -> y.getTurf().compareTo(x.getTurf()))
				.collect(Collectors.toList());

		logger.info("Found {} weapons..", allWeaponStats.size());

		boolean sendAllWeapons = false;

		for (Splatoon2WeaponStats weaponStats : allWeaponStats) {
			logger.info("Next weapon: {}..", weaponStats.getWeaponId());

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
					.filter(Objects::nonNull)
					.reduce((first, second) -> second)
					.orElse(weaponStats.getCurrentFlag());

			double maxFlag = yesterdayMatchesForWeapon.stream()
					.map(Splatoon2Match::getCurrentFlag)
					.filter(Objects::nonNull)
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
//					.append(";").append(100_000 - weaponStats.getTurf() > 0 ? 100_000 - weaponStats.getTurf() : 0)
					.append(";").append(yesterdayPaint)
					.append(";").append(String.format(Locale.US, "%.2f", calculateAvgPaint(weaponStats.getTurf(), getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))))
					.append(";").append(getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))
					.append(";").append(getNumber(weaponStats.getWins()))
					.append(";").append(getNumber(weaponStats.getDefeats()))
					.append(";").append(String.format(Locale.US, "%.2f", getNumber(weaponStats.getWins()) * 1.0d / (getNumber(weaponStats.getWins()) + getNumber(weaponStats.getDefeats()))))
					.append(";").append(yesterdayWins)
					.append(";").append(yesterdayDefeats)
					.append(";").append(String.format(Locale.US, "%.1f", currentFlag))
//					.append(";").append(String.format(Locale.US, "%.1f", currentFlag - currentFlagDelta))
					.append(";").append(String.format(Locale.US, "%.1f", maxFlag))
//					.append(";").append(String.format(Locale.US, "%.1f", (maxFlag > maxFlagDelta) ? maxFlag - maxFlagDelta : 0.0))
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

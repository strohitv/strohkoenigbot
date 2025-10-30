package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSpecialWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Match;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Log4j2
public class S3StreamStatistics {
	private final ObjectMapper mapper = new ObjectMapper();

	private final Splatoon3VsRotationRepository rotationRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final Splatoon3BadgeRepository badgeRepository;

	private final ImageService imageService;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final S3BadgeSender badgeSender;

	private final DecimalFormat df = new DecimalFormat("#,###,###");

	private final List<Splatoon3VsResult> includedMatches = new ArrayList<>();

	private Double startXZones, startXTower, startXRainmaker, startXClams;
	private Double currentXZones, currentXTower, currentXRainmaker, currentXClams;

	private List<Weapon> startWeaponStats, currentWeaponStats;

	private List<Gear> headGears, clothingGears, shoesGears;

	private Map<Splatoon3VsSpecialWeapon, Integer> startSpecialWinStats, currentSpecialWinStats;

	private final String path = String.format("%s/src/main/resources/html/s3/onstream-statistics-filled.html", Paths.get(".").toAbsolutePath().normalize());

	private String currentHtml = "<!DOCTYPE html>\n" +
		"<html lang=\"en\">\n" +
		"\n" +
		"<head>\n" +
		"\t<meta charset=\"UTF-8\">\n" +
		"\t<meta http-equiv=\"refresh\" content=\"30\">\n" +
		"\t<title>Splatoon 3 statistics</title>\n" +
		"</head>\n" +
		"<body>\n" +
		"</body>\n" +
		"</html>";

	@Getter
	private String finishedHtml = currentHtml;

	@Getter
	private Instant lastUpdate = null;

	public S3StreamStatistics(@Autowired Splatoon3VsRotationRepository rotationRepository,
							  @Autowired Splatoon3VsModeRepository modeRepository,
							  @Autowired Splatoon3BadgeRepository badgeRepository,
							  @Autowired ImageService imageService,
							  @Autowired LogSender logSender,
							  @Autowired ExceptionLogger exceptionLogger,
							  @Autowired S3BadgeSender badgeSender) {
		this.rotationRepository = rotationRepository;
		this.modeRepository = modeRepository;
		this.badgeRepository = badgeRepository;
		this.imageService = imageService;
		this.logSender = logSender;
		this.exceptionLogger = exceptionLogger;
		this.badgeSender = badgeSender;

		reset();
	}

	public void reset() {
		includedMatches.clear();
		startXZones = startXTower = startXRainmaker = startXClams = currentXZones = currentXTower = currentXRainmaker = currentXClams = null;
		headGears = clothingGears = shoesGears = null;
		startWeaponStats = currentWeaponStats = null;
		startSpecialWinStats = currentSpecialWinStats = null;

		try (var is = this.getClass().getClassLoader().getResourceAsStream("html/s3/afterstream-statistics-template.html")) {
			assert is != null;
			currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			finishedHtml = currentHtml;

			try (var myWriter = new FileWriter(path)) {
				myWriter.write(currentHtml);
			}
		} catch (Exception e) {
			log.error(e);
		}

		lastUpdate = null;
	}

	public void exportHtml() {
		if (!includedMatches.isEmpty() && startSpecialWinStats != null) {
			lastUpdate = Instant.now();

			long victoryCount = includedMatches.stream().filter(m -> "win".equalsIgnoreCase(m.getOwnJudgement())).count();
			long defeatCount = includedMatches.stream().filter(m -> "lose".equalsIgnoreCase(m.getOwnJudgement()) || "deemed_lose".equalsIgnoreCase(m.getOwnJudgement())).count();

			long victoryRatio = 100 * victoryCount / Math.max(victoryCount + defeatCount, 1);
			long defeatRatio = 100 - victoryRatio;

			var lastMatch = includedMatches.stream()
				.min((a, b) -> b.getPlayedTime().compareTo(a.getPlayedTime()))
				.orElse(null);

			var team = lastMatch.getTeams().stream()
				.filter(Splatoon3VsResultTeam::getIsMyTeam)
				.findFirst()
				.orElse(null);

			if (team == null) {
				return;
			}

			var player = team.getTeamPlayers().stream()
				.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
				.findFirst()
				.orElse(null);

			if (player == null) {
				return;
			}

			var mode = modeRepository.findByApiTypenameAndApiModeDistinction("XMatchSetting", null)
				.orElse(null);

			if (mode == null) {
				return;
			}

			var rotation = rotationRepository.findByModeAndStartTime(mode, getSlotStartTime(Instant.now()))
				.orElse(null);

			Double currentPower = null;
			Double startPower = null;
			boolean zonesHidden = true, towerHidden = true, rainmakerHidden = true, clamsHidden = true;
			if (rotation != null) {
				switch (rotation.getRule().getApiRule()) {
					case "AREA":
						currentPower = currentXZones;
						startPower = startXZones;
						zonesHidden = false;
						break;
					case "LOFT":
						currentPower = currentXTower;
						startPower = startXTower;
						towerHidden = false;
						break;
					case "GOAL":
						currentPower = currentXRainmaker;
						startPower = startXRainmaker;
						rainmakerHidden = false;
						break;
					case "CLAM":
						currentPower = currentXClams;
						startPower = startXClams;
						clamsHidden = false;
						break;
					default:
						return;
				}
			}

			var weaponStatsStart = startWeaponStats.stream()
				.filter(w -> w.getId().equalsIgnoreCase(player.getWeapon().getApiId()))
				.findFirst()
				.orElse(null);

			var weaponStatsCurrent = currentWeaponStats.stream()
				.filter(w -> w.getId().equalsIgnoreCase(player.getWeapon().getApiId()))
				.findFirst()
				.orElse(null);

			if (weaponStatsStart == null || weaponStatsCurrent == null) {
				return;
			}

			String kills = "-", assists = "-", deaths = "-", specials = "-", paint = "-";
			String killsColor = "", assistsColor = "", deathsColor = "", specialsColor = "";
			if (player.getKills() != null) {
				if (player.getKills() >= 10) {
					deathsColor = "green";
				} else if (player.getKills() <= 3) {
					deathsColor = "red";
				}

				killsColor = "green";
				kills = String.valueOf(player.getKills());

				if (player.getAssists() != null) {
					kills = String.valueOf(player.getKills() - player.getAssists());
				}
			}
			if (player.getAssists() != null) {
				if (player.getAssists() >= 5) {
					assistsColor = "green";
				}

				assists = String.valueOf(player.getAssists());
			}
			if (player.getDeaths() != null) {
				if (player.getDeaths() >= 8) {
					deathsColor = "red";
				} else if (player.getDeaths() <= 3) {
					deathsColor = "green";
				}

				deaths = String.valueOf(player.getDeaths());
			}
			if (player.getSpecials() != null) {
				if (player.getSpecials() >= 5) {
					specialsColor = "green";
				}

				specials = String.valueOf(player.getSpecials());
			}
			if (player.getPaint() != null) {
				paint = df.format(player.getPaint()).replaceAll(",", " ");
			}

			var lastMatchWasOpenWithFriends = "BANKARA".equals(lastMatch.getMode().getApiMode()) && "OPEN".equals(lastMatch.getMode().getApiModeDistinction()) && lastMatch.getShortenedJson() != null && lastMatch.getShortenedJson().contains("\"bankaraPower\":{\"power\":");
			Double openCurrentPower = null;
			Double openPreviousPower = null;
			Double openMaxPower = null;
			boolean openZonesHidden = true, openTowerHidden = true, openRainmakerHidden = true, openClamsHidden = true;

			if (lastMatchWasOpenWithFriends) {
				switch (lastMatch.getRule().getApiRule()) {
					case "AREA":
						openZonesHidden = false;
						break;
					case "LOFT":
						openTowerHidden = false;
						break;
					case "GOAL":
						openRainmakerHidden = false;
						break;
					case "CLAM":
						openClamsHidden = false;
						break;
					default:
						return;
				}

				var allOpenMatchesThisRotation = includedMatches.stream()
					.filter(m -> m.getRotation() != null)
					.filter(m -> Objects.equals(m.getRotation().getId(), lastMatch.getRotation().getId()))
					.collect(Collectors.toList());

				try {
					var currentMatchParsed = mapper.readValue(imageService.restoreJson(lastMatch.getShortenedJson()), BattleResult.class)
						.getData()
						.getVsHistoryDetail()
						.getBankaraMatch();

					openCurrentPower = currentMatchParsed.getBankaraPower() != null ? currentMatchParsed.getBankaraPower().getPower() : null;

//					logSender.sendLogs(log, "number of matches in this rotation: `%d`", allOpenMatchesThisRotation.size());
//
//					logSender.sendLogs(log, "Power of games in this rotation: \n%s", includedMatches.stream()
//						.filter(m -> m.getRotation() != null)
//						.filter(m -> Objects.equals(m.getRotation().getId(), lastMatch.getRotation().getId()))
//						.map(m -> String.format("- power: `%s` - time: `%s` - id: `%s` - rotation-id: `%s`", getPower(m), m.getPlayedTime(), m.getId(), m.getRotation().getId()))
//						.reduce((a, b) -> String.format("%s\n%s", a, b))
//						.orElse(""));

					if (allOpenMatchesThisRotation.size() > 1) {
						var previousMatch = allOpenMatchesThisRotation.stream()
							.filter(m -> m.getPlayedTime() != null && m.getPlayedTime().isBefore(lastMatch.getPlayedTime()))
							.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime));

						if (previousMatch.isPresent()) {
							var unpackedPreviousMatch = previousMatch.get();

							var previousMatchParsed = mapper.readValue(imageService.restoreJson(unpackedPreviousMatch.getShortenedJson()), BattleResult.class)
								.getData()
								.getVsHistoryDetail()
								.getBankaraMatch();

							openPreviousPower = previousMatchParsed.getBankaraPower() != null ? previousMatchParsed.getBankaraPower().getPower() : null;
						}
					}
				} catch (JsonProcessingException ex) {
					exceptionLogger.logExceptionAsAttachment(log, "Exception occured during JSON processing!", ex);
				}

				openMaxPower = allOpenMatchesThisRotation.stream()
					.map(m -> {
						try {
							return mapper.readValue(imageService.restoreJson(m.getShortenedJson()), BattleResult.class);
						} catch (JsonProcessingException e) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.map(m -> m.getData().getVsHistoryDetail().getBankaraMatch().getBankaraPower())
					.filter(Objects::nonNull)
					.map(Match.BankaraPower::getPower)
					.filter(Objects::nonNull)
					.max(Comparator.naturalOrder())
					.orElse(null);
			}

			var openChangeHidden = openCurrentPower == null
				|| openPreviousPower == null
				|| openCurrentPower.doubleValue() == openPreviousPower.doubleValue();

			var lastMatchWasSeries = "BANKARA".equals(lastMatch.getMode().getApiMode()) && "CHALLENGE".equals(lastMatch.getMode().getApiModeDistinction());
			Double seriesCurrentPower = null;
			Double seriesPreviousPower = null;
			Double seriesMaxPower = null;

			if (lastMatchWasSeries) {
				var allSeriesMatchesOfThisWeapon = includedMatches.stream()
					.filter(m -> "BANKARA".equals(m.getMode().getApiMode()) && "CHALLENGE".equals(m.getMode().getApiModeDistinction()))
					.filter(m ->
						player.getWeapon().equals(m.getTeams().stream()
							.filter(Splatoon3VsResultTeam::getIsMyTeam)
							.flatMap(t -> t.getTeamPlayers().stream())
							.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
							.map(Splatoon3VsResultTeamPlayer::getWeapon)
							.findFirst()
							.orElse(null)))
					.collect(Collectors.toList());

				try {
					var currentMatchParsed = mapper.readValue(imageService.restoreJson(lastMatch.getShortenedJson()), BattleResult.class)
						.getData()
						.getVsHistoryDetail()
						.getBankaraMatch();

					seriesCurrentPower = currentMatchParsed.getWeaponPower();

//					logSender.sendLogs(log, "number of matches in this rotation: `%d`", allSeriesMatchesOfThisWeapon.size());
//
//					logSender.sendLogs(log, "Power of games in this rotation: \n%s", includedMatches.stream()
//						.filter(m -> m.getRotation() != null)
//						.filter(m -> Objects.equals(m.getRotation().getId(), lastMatch.getRotation().getId()))
//						.map(m -> String.format("- power: `%s` - time: `%s` - id: `%s` - rotation-id: `%s`", getPower(m), m.getPlayedTime(), m.getId(), m.getRotation().getId()))
//						.reduce((a, b) -> String.format("%s\n%s", a, b))
//						.orElse(""));

					if (allSeriesMatchesOfThisWeapon.size() > 1) {
						var previousMatch = allSeriesMatchesOfThisWeapon.stream()
							.filter(m -> m.getPlayedTime() != null && m.getPlayedTime().isBefore(lastMatch.getPlayedTime()))
							.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime));

						if (previousMatch.isPresent()) {
							var unpackedPreviousMatch = previousMatch.get();

							var previousMatchParsed = mapper.readValue(imageService.restoreJson(unpackedPreviousMatch.getShortenedJson()), BattleResult.class)
								.getData()
								.getVsHistoryDetail()
								.getBankaraMatch();

							seriesPreviousPower = previousMatchParsed.getWeaponPower();
						}
					}
				} catch (JsonProcessingException ex) {
					exceptionLogger.logExceptionAsAttachment(log, "Exception occured during JSON processing!", ex);
				}

				seriesMaxPower =
					allSeriesMatchesOfThisWeapon.stream()
						.map(m -> {
							try {
								return mapper.readValue(imageService.restoreJson(m.getShortenedJson()), BattleResult.class);
							} catch (JsonProcessingException e) {
								return null;
							}
						})
						.filter(Objects::nonNull)
						.map(m -> m.getData().getVsHistoryDetail().getBankaraMatch())
						.filter(Objects::nonNull)
						.map(Match::getWeaponPower)
						.filter(Objects::nonNull)
						.max(Comparator.naturalOrder())
						.orElse(null);

				var weaponMaxPower =
					startWeaponStats.stream()
						.filter(sws -> Objects.equals(player.getWeapon().getName(), sws.getName()))
						.findFirst()
						.map(w -> w.getStats().getMaxWeaponPower())
						.orElse(null);

				if (seriesMaxPower == null || weaponMaxPower != null && weaponMaxPower > seriesMaxPower) {
					seriesMaxPower = weaponMaxPower;
				}
			}

			var seriesChangeHidden = seriesCurrentPower == null
				|| seriesPreviousPower == null
				|| seriesCurrentPower.doubleValue() == seriesPreviousPower.doubleValue();

			var specialWeapon = extractSpecialWeapon(lastMatch);
			int specialWeaponWins = 0;
			int specialWeaponWinsDifference = 0;
			var badgeImageBase64 = getImageEncoded(player.getWeapon().getSpecialWeapon().getImage());

			if (specialWeapon != null) {
				specialWeaponWins = currentSpecialWinStats.getOrDefault(specialWeapon, 0);

//				logSender.sendLogs(log,
//					"special weapon found in export! It is: `%s`, wins: `%d`",
//					specialWeapon.getName(),
//					specialWeaponWins);

				final int tempSpecialWeaponWins = specialWeaponWins;
				var possibleBadgeVariants = Stream.of(30, 180, 1200)
					.filter(pbv -> pbv <= tempSpecialWeaponWins)
					.collect(Collectors.toList());

				specialWeaponWinsDifference = specialWeaponWins - startSpecialWinStats.getOrDefault(specialWeapon, specialWeaponWins);

				final var finalSpecialWeapon = specialWeapon;
				var badges = badgeRepository.findAll()
					.stream()
					.filter(b -> b.getDescription() != null && b.getDescription().contains(String.format("Wins with %s", finalSpecialWeapon.getName())))
					.collect(Collectors.toList());

				for (var badgeVariant : possibleBadgeVariants) {
					var badgeInDb = badges.stream()
						.filter(bDb -> bDb.getDescription()
							.split(" ")[0]
							.replaceAll("[^0-9]", "")
							.equals(String.format("%d", badgeVariant)))
						.findFirst()
						.orElse(null);

					if (badgeInDb == null) {
						badgeSender.reloadBadges();
						badges = badgeRepository.findAll()
							.stream()
							.filter(b -> b.getDescription() != null && b.getDescription().contains(String.format("Wins with %s", finalSpecialWeapon.getName())))
							.collect(Collectors.toList());

						badgeInDb = badges.stream()
							.filter(bDb -> bDb.getDescription()
								.split(" ")[0]
								.replaceAll("[^0-9]", "")
								.equals(String.format("%d", badgeVariant)))
							.findFirst()
							.orElse(null);

						if (badgeInDb == null) continue;
					}

//					logSender.sendLogs(log,
//						"Setting badge to `%s`",
//						badgeInDb.getDescription());

					badgeImageBase64 = getImageEncoded(badgeInDb.getImage());
				}
			} else {
				logSender.sendLogs(log, "special weapon is null wtf, last result: `%d`", lastMatch.getId());
			}

			var startExpWeapon = getWeaponExp(weaponStatsStart.getStats().getLevel(), weaponStatsStart.getStats().getExpToLevelUp());
			var currentExpWeapon = getWeaponExp(weaponStatsCurrent.getStats().getLevel(), weaponStatsCurrent.getStats().getExpToLevelUp());
			var expWeaponGain = currentExpWeapon - startExpWeapon;

			var expGoal = getExpGoal(weaponStatsCurrent.getStats().getLevel());
			var alreadyOwnedExpRatio = startExpWeapon * 100.0 / expGoal;
			var earnedExpStreamRatio = expWeaponGain * 100.0 / expGoal;
			var remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;

			if (weaponStatsCurrent.getStats().getLevel() >= 5) {
				var previousLevelExp = getExpGoal(weaponStatsCurrent.getStats().getLevel() - 1);
				alreadyOwnedExpRatio = (startExpWeapon - previousLevelExp) * 100.0 / (expGoal - previousLevelExp);
				earnedExpStreamRatio = expWeaponGain * 100.0 / (expGoal - previousLevelExp);
				remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;
			}

			var mainWeaponImage = player.getWeapon().getImage();

			if (weaponStatsCurrent.getStats().getLevel() < 4) {
				if (player.getWeapon().getImage3D() != null) {
					mainWeaponImage = player.getWeapon().getImage3D();
				} else if (player.getWeapon().getImage3DThumbnail() != null) {
					mainWeaponImage = player.getWeapon().getImage3DThumbnail();
				}
			}

			var mainWeaponUrl = getImageEncoded(mainWeaponImage);

			var currentLevelToCheck = 4;
			var hasReloaded = false;
			while (weaponStatsCurrent.getStats().getLevel() >= currentLevelToCheck) {
				var badgeSearchResult = searchForBadge(currentLevelToCheck, weaponStatsCurrent.getName(), !hasReloaded);

				hasReloaded |= badgeSearchResult.isHasReloaded();
				if (badgeSearchResult.getBadge().isPresent()) {
					// make the found badge the main weapon image
					mainWeaponUrl = getImageEncoded(badgeSearchResult.getBadge().get().getImage());
				}

				currentLevelToCheck++;
			}

			String subWeaponUrl = getImageEncoded(player.getWeapon().getSubWeapon().getImage());
			String specialWeaponUrl = getImageEncoded(player.getWeapon().getSpecialWeapon().getImage());

			String headGear = getImageEncoded(player.getHeadGear().getOriginalImage());
			String headGearMain = getImageEncoded(player.getHeadGearMainAbility().getImage());
			String headGearSub1 = getImageEncoded(player.getHeadGearSecondaryAbility1().getImage());
			String headGearSub2 = player.getHeadGearSecondaryAbility2() != null ? getImageEncoded(player.getHeadGearSecondaryAbility2().getImage()) : null;
			String headGearSub3 = player.getHeadGearSecondaryAbility3() != null ? getImageEncoded(player.getHeadGearSecondaryAbility3().getImage()) : null;
			var headGearRarity = headGears.stream()
				.filter(g -> g.getName().equalsIgnoreCase(player.getHeadGear().getName()))
				.findFirst()
				.map(Gear::getRarity)
				.orElse(2);

			String clothesGear = getImageEncoded(player.getClothingGear().getOriginalImage());
			String clothesGearMain = getImageEncoded(player.getClothingMainAbility().getImage());
			String clothesGearSub1 = getImageEncoded(player.getClothingSecondaryAbility1().getImage());
			String clothesGearSub2 = player.getClothingSecondaryAbility2() != null ? getImageEncoded(player.getClothingSecondaryAbility2().getImage()) : null;
			String clothesGearSub3 = player.getClothingSecondaryAbility3() != null ? getImageEncoded(player.getClothingSecondaryAbility3().getImage()) : null;
			var clothingGearRarity = clothingGears.stream()
				.filter(g -> g.getName().equalsIgnoreCase(player.getClothingGear().getName()))
				.findFirst()
				.map(Gear::getRarity)
				.orElse(2);

			String shoesGear = getImageEncoded(player.getShoesGear().getOriginalImage());
			String shoesGearMain = getImageEncoded(player.getShoesMainAbility().getImage());
			String shoesGearSub1 = getImageEncoded(player.getShoesSecondaryAbility1().getImage());
			String shoesGearSub2 = player.getShoesSecondaryAbility2() != null ? getImageEncoded(player.getShoesSecondaryAbility2().getImage()) : null;
			String shoesGearSub3 = player.getShoesSecondaryAbility3() != null ? getImageEncoded(player.getShoesSecondaryAbility3().getImage()) : null;
			var shoesGearRarity = shoesGears.stream()
				.filter(g -> g.getName().equalsIgnoreCase(player.getShoesGear().getName()))
				.findFirst()
				.map(Gear::getRarity)
				.orElse(2);

//			logSender.sendLogs(log, String.format("openCurrentPower: `%s`, openPreviousPower: `%s`, openChangeHidden: `%s`, openCurrentPower == null: `%s`, openPreviousPower == null: `%s`, openCurrentPower.doubleValue() == openPreviousPower.doubleValue(): `%s`",
//				openCurrentPower != null ? String.format("%.1f", openCurrentPower) : "null",
//				openPreviousPower != null ? String.format("%.1f", openPreviousPower) : "null",
//				openChangeHidden,
//				openCurrentPower == null,
//				openPreviousPower == null,
//				Objects.equals(openCurrentPower, openPreviousPower)));

			try (var is = this.getClass().getClassLoader().getResourceAsStream("html/s3/onstream-statistics-template.html")) {
				assert is != null;
				currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

				currentHtml = currentHtml
					.replace("{main-weapon}", String.format("data:image/png;base64,%s", mainWeaponUrl))
					.replace("{sub-weapon}", String.format("data:image/png;base64,%s", subWeaponUrl))
					.replace("{special-weapon}", String.format("data:image/png;base64,%s", specialWeaponUrl))
					.replace("{head}", String.format("data:image/png;base64,%s", headGear))
					.replace("{head-stars}", String.format("%d", headGearRarity))
					.replace("{head-main}", String.format("data:image/png;base64,%s", headGearMain))
					.replace("{head-sub-1}", String.format("data:image/png;base64,%s", headGearSub1))
					.replace("{clothing}", String.format("data:image/png;base64,%s", clothesGear))
					.replace("{clothing-stars}", String.format("%d", clothingGearRarity))
					.replace("{clothing-main}", String.format("data:image/png;base64,%s", clothesGearMain))
					.replace("{clothing-sub-1}", String.format("data:image/png;base64,%s", clothesGearSub1))
					.replace("{shoes}", String.format("data:image/png;base64,%s", shoesGear))
					.replace("{shoes-stars}", String.format("%d", shoesGearRarity))
					.replace("{shoes-main}", String.format("data:image/png;base64,%s", shoesGearMain))
					.replace("{shoes-sub-1}", String.format("data:image/png;base64,%s", shoesGearSub1))

					.replace("{weapon-star-1-hidden}", weaponStatsCurrent.getStats().getLevel() >= 1 ? "" : "hidden")
					.replace("{weapon-star-2-hidden}", weaponStatsCurrent.getStats().getLevel() >= 2 ? "" : "hidden")
					.replace("{weapon-star-3-hidden}", weaponStatsCurrent.getStats().getLevel() >= 3 ? "" : "hidden")
					.replace("{weapon-star-4-hidden}", weaponStatsCurrent.getStats().getLevel() >= 4 ? "" : "hidden")
					.replace("{weapon-star-5-hidden}", weaponStatsCurrent.getStats().getLevel() >= 5 ? "" : "hidden")
					.replace("{weapon-star-low-hidden}", weaponStatsCurrent.getStats().getLevel() < 6 ? "" : "hidden")
					.replace("{weapon-star-high-hidden}", weaponStatsCurrent.getStats().getLevel() >= 6 ? "" : "hidden")
					.replace("{weapon-level}", String.format("%d", weaponStatsCurrent.getStats().getLevel()))
					.replace("{exp-hidden}", weaponStatsCurrent.getStats().getLevel() >= 10 ? "hidden" : "")
					.replace("{weapon-win-count}", df.format(weaponStatsCurrent.getStats().getWin()).replaceAll(",", " "))

					.replace("{weapon-tab}", weaponStatsCurrent.getStats().getLevel() >= 10 ? "hidden" : "tab")
					.replace("{weapon-exp-start}", df.format(startExpWeapon).replaceAll(",", " "))
					.replace("{weapon-exp}", df.format(currentExpWeapon).replaceAll(",", " "))

					.replace("{weapon-exp-gain-hidden}", currentExpWeapon == startExpWeapon ? "hidden" : "")
					.replace("{weapon-exp-gain}", df.format(expWeaponGain).replaceAll(",", " "))

					.replace("{already-owned-exp-ratio}", String.format(Locale.US, "%.2f", alreadyOwnedExpRatio))
					.replace("{earned-exp-stream-ratio}", String.format(Locale.US, "%.2f", earnedExpStreamRatio))
					.replace("{remaining-exp-ratio}", String.format(Locale.US, "%.2f", remainingExpRatio))

					.replace("{wins}", String.format("%d", victoryCount))
					.replace("{defeats}", String.format("%d", defeatCount))
					.replace("{win-ratio}", String.format("%d", victoryRatio))
					.replace("{defeat-ratio}", String.format("%d", defeatRatio))

					.replace("{kills}", kills)
					.replace("{kills-color}", killsColor)
					.replace("{assists}", assists)
					.replace("{assists-color}", assistsColor)
					.replace("{deaths}", deaths)
					.replace("{deaths-color}", deathsColor)
					.replace("{specials}", specials)
					.replace("{specials-color}", specialsColor)
					.replace("{paint}", paint)

					.replace("{special-tab}", (specialWeapon != null) ? "tab" : "")
					.replace("{special-weapon-badge}", String.format("data:image/png;base64,%s", badgeImageBase64))
					.replace("{special-weapon-wins}", df.format(specialWeaponWins).replaceAll(",", " "))
					.replace("{special-weapon-wins-change-hidden}", (specialWeaponWinsDifference <= 0) ? "hidden" : "")
					.replace("{special-weapon-wins-change}", df.format(specialWeaponWinsDifference).replaceAll(",", " "))

					.replace("{zones-icon-hidden}", zonesHidden ? "hidden" : "")
					.replace("{tower-icon-hidden}", towerHidden ? "hidden" : "")
					.replace("{rainmaker-icon-hidden}", rainmakerHidden ? "hidden" : "")
					.replace("{clams-icon-hidden}", clamsHidden ? "hidden" : "")
					.replace("{x-tab}", "X_MATCH".equals(lastMatch.getMode().getApiMode()) ? "tab" : "")
					.replace("{current-x}", buildCurrentPower(currentPower))
					.replace("{x-change-hidden}", currentPower == null
						|| startPower == null
						|| currentPower.doubleValue() == startPower.doubleValue() ? "hidden" : "")
					.replace("{x-change}", buildPowerDiff(startPower, currentPower))
					.replace("{x-change-color}", getPowerDiffColor(startPower, currentPower))

					.replace("{open-tab}", lastMatchWasOpenWithFriends ? "tab" : "")
					.replace("{open-zones-icon-hidden}", openZonesHidden ? "hidden" : "")
					.replace("{open-tower-icon-hidden}", openTowerHidden ? "hidden" : "")
					.replace("{open-rainmaker-icon-hidden}", openRainmakerHidden ? "hidden" : "")
					.replace("{open-clams-icon-hidden}", openClamsHidden ? "hidden" : "")
					.replace("{open-change-hidden}", openChangeHidden ? "hidden" : "")
					.replace("{open-change-color}", getPowerDiffColor(openPreviousPower, openCurrentPower))
					.replace("{open-max-hidden}", openMaxPower != null ? "" : "hidden")
					.replace("{current-open}", buildCurrentPower(openCurrentPower))
					.replace("{open-change}", buildPowerDiff(openPreviousPower, openCurrentPower))
					.replace("{open-max}", buildCurrentPower(openMaxPower))

					.replace("{series-tab}", lastMatchWasSeries ? "tab" : "")
					.replace("{series-weapon-icon}", lastMatchWasSeries ? String.format("data:image/png;base64,%s", getImageEncoded(player.getWeapon().getImage())) : "data:image/png;base64,0")
					.replace("{series-change-hidden}", seriesChangeHidden ? "hidden" : "")
					.replace("{series-change-color}", getPowerDiffColor(seriesPreviousPower, seriesCurrentPower))
					.replace("{series-max-hidden}", seriesMaxPower != null ? "" : "hidden")
					.replace("{current-series}", buildCurrentPower(seriesCurrentPower))
					.replace("{series-change}", buildPowerDiff(seriesPreviousPower, seriesCurrentPower))
					.replace("{series-max}", buildCurrentPower(seriesMaxPower))
				;

				if (headGearSub2 != null) {
					currentHtml = currentHtml.replace("{head-sub-2}", String.format("data:image/png;base64,%s", headGearSub2))
						.replace("{head-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{head-sub-2}", "")
						.replace("{head-sub2-hidden}", "hidden");
				}

				if (headGearSub3 != null) {
					currentHtml = currentHtml.replace("{head-sub-3}", String.format("data:image/png;base64,%s", headGearSub3))
						.replace("{head-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{head-sub-3}", "")
						.replace("{head-sub3-hidden}", "hidden");
				}

				if (clothesGearSub2 != null) {
					currentHtml = currentHtml.replace("{clothing-sub-2}", String.format("data:image/png;base64,%s", clothesGearSub2))
						.replace("{clothing-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{clothing-sub-2}", "")
						.replace("{clothing-sub2-hidden}", "hidden");
				}

				if (clothesGearSub3 != null) {
					currentHtml = currentHtml.replace("{clothing-sub-3}", String.format("data:image/png;base64,%s", clothesGearSub3))
						.replace("{clothing-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{clothing-sub-3}", "")
						.replace("{clothing-sub3-hidden}", "hidden");
				}

				if (shoesGearSub2 != null) {
					currentHtml = currentHtml.replace("{shoes-sub-2}", String.format("data:image/png;base64,%s", shoesGearSub2))
						.replace("{shoes-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{shoes-sub-2}", "")
						.replace("{shoes-sub2-hidden}", "hidden");
				}

				if (shoesGearSub3 != null) {
					currentHtml = currentHtml.replace("{shoes-sub-3}", String.format("data:image/png;base64,%s", shoesGearSub3))
						.replace("{shoes-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{shoes-sub-3}", "")
						.replace("{shoes-sub3-hidden}", "hidden");
				}

				finishedHtml = currentHtml;

				try (var myWriter = new FileWriter(path)) {
					myWriter.write(currentHtml);
				}
			} catch (Exception e) {
				log.error(e);
			}
		}
	}

//	private Double getPower(Splatoon3VsResult m) {
//		Match previousMatchParsed = null;
//		try {
//			previousMatchParsed = mapper.readValue(imageService.restoreJson(m.getShortenedJson()), BattleResult.class)
//				.getData()
//				.getVsHistoryDetail()
//				.getBankaraMatch();
//
//			return previousMatchParsed.getBankaraPower() != null ? previousMatchParsed.getBankaraPower().getPower() : null;
//		} catch (JsonProcessingException ignored) {
//			return null;
//		}
//	}

	private int getExpGoal(Integer level) {
		var expGoal = 0;

		switch (level) {
			case 1: {
				expGoal = 25000;
				break;
			}
			case 2: {
				expGoal = 60000;
				break;
			}
			case 3: {
				expGoal = 160000;
				break;
			}
			case 4: {
				expGoal = 1160000;
				break;
			}
			case 5: {
				expGoal = 2000000;
				break;
			}
			case 6: {
				expGoal = 3000000;
				break;
			}
			case 7: {
				expGoal = 4000000;
				break;
			}
			case 8: {
				expGoal = 5000000;
				break;
			}
			case 9:
			case 10: {
				expGoal = 6000000;
				break;
			}
			case 0:
			default: {
				expGoal = 5000;
				break;
			}
		}

		return expGoal;
	}

	private int getWeaponExp(Integer level, Integer expToLevelUp) {
		var currentExp = 0;

		switch (level) {
			case 1: {
				currentExp = 25000 - expToLevelUp;
				break;
			}
			case 2: {
				currentExp = 60000 - expToLevelUp;
				break;
			}
			case 3: {
				currentExp = 160000 - expToLevelUp;
				break;
			}
			case 4: {
				currentExp = 1160000 - expToLevelUp;
				break;
			}
			case 5: {
				currentExp = 2000000 - expToLevelUp;
				break;
			}
			case 6: {
				currentExp = 3000000 - expToLevelUp;
				break;
			}
			case 7: {
				currentExp = 4000000 - expToLevelUp;
				break;
			}
			case 8: {
				currentExp = 5000000 - expToLevelUp;
				break;
			}
			case 9: {
				currentExp = 6000000 - expToLevelUp;
				break;
			}
			case 10: {
				currentExp = 6000000;
				break;
			}
			case 0:
			default: {
				currentExp = 5000 - expToLevelUp;
				break;
			}
		}

		return currentExp;
	}

	private BadgeReloadResult searchForBadge(int level, String weaponName, boolean reloadAllowed) {
		// example: 5★ S-BLAST '91 User
		var weaponBadge = badgeRepository.findAll()
			.stream()
			.filter(b -> b.getDescription() != null && b.getDescription().contains(String.format("%d★ %s User", level, weaponName)))
			.findFirst();

		var hasReloaded = false;
		if (weaponBadge.isEmpty() && reloadAllowed) {
			badgeSender.reloadBadges();
			hasReloaded = true;

			weaponBadge = badgeRepository.findAll()
				.stream()
				.filter(b -> b.getDescription() != null && b.getDescription().contains(String.format("%d★ %s User", level, weaponName)))
				.findFirst();
		}

		return new BadgeReloadResult(hasReloaded, weaponBadge);
	}

	@Getter
	@RequiredArgsConstructor
	private static class BadgeReloadResult {
		private final boolean hasReloaded;
		private final Optional<Splatoon3Badge> badge;
	}

	private String buildCurrentPower(Double currentPower) {
		if (currentPower == null) {
			return "-";
		} else {
			return String.format("%.1f", currentPower);
		}
	}

	private String getPowerDiffColor(Double startPower, Double currentPower) {
		if (startPower == null || currentPower == null || startPower.doubleValue() == currentPower.doubleValue()) {
			return "";
		} else if (startPower < currentPower) {
			return "green";
		} else {
			return "red";
		}
	}

	private String buildPowerDiff(Double startPower, Double currentPower) {
		if (startPower == null || currentPower == null) {
			return "";
		}

		var template = "&#177; 0.0";

		if (startPower < currentPower) {
			template = "+ %.1f";
		} else if (startPower > currentPower) {
			template = "- %.1f";
		}

		return String.format(template, Math.abs(currentPower - startPower));
	}

	public void addGames(List<Splatoon3VsResult> games) {
		var newGames = games.stream()
			.filter(game -> !includedMatches.contains(game))
			.collect(Collectors.toList());

		newGames.forEach(game -> {
			if (!game.getMode().getName().toLowerCase().contains("private")
				&& game.getOwnJudgement().toLowerCase().contains("win")
				&& game.getTeams().stream()
				.filter(Splatoon3VsResultTeam::getIsMyTeam)
				.flatMap(t -> t.getTeamPlayers().stream())
				.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
				.findFirst().map(Splatoon3VsResultTeamPlayer::getSpecials).orElse(0) > 0) {

				var specialWeapon = extractSpecialWeapon(game);
				if (specialWeapon == null) return;

				currentSpecialWinStats.putIfAbsent(specialWeapon, 0);
				currentSpecialWinStats.put(specialWeapon, currentSpecialWinStats.get(specialWeapon) + 1);
//				logSender.sendLogs(log,
//					"special weapon found in game! It is: `%s`, wins: `%d`",
//					specialWeapon.getName(),
//					currentSpecialWinStats.get(specialWeapon));
			}
		});

		includedMatches.addAll(newGames);
	}

	private Splatoon3VsSpecialWeapon extractSpecialWeapon(Splatoon3VsResult result) {
		return result.getTeams().stream()
			.filter(Splatoon3VsResultTeam::getIsMyTeam)
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.map(tp -> tp.getWeapon().getSpecialWeapon())
			.orElse(null);
	}

	public void setCurrentXPowers(Double zones, Double tower, Double rainmaker, Double clams) {
		if (startXZones == null) {
			startXZones = zones;
		}
		if (startXTower == null) {
			startXTower = tower;
		}
		if (startXRainmaker == null) {
			startXRainmaker = rainmaker;
		}
		if (startXClams == null) {
			startXClams = clams;
		}

		currentXZones = zones;
		currentXTower = tower;
		currentXRainmaker = rainmaker;
		currentXClams = clams;
	}

	public void setCurrentWeaponRecords(Weapon[] allWeapons) {
		if (startWeaponStats == null) {
			startWeaponStats = Arrays.stream(allWeapons).collect(Collectors.toList());
		}

		currentWeaponStats = Arrays.stream(allWeapons).collect(Collectors.toList());
	}

	public void setCurrentGears(Gear[] allHeadGears, Gear[] allClothingGears, Gear[] allShoesGears) {
		headGears = Arrays.stream(allHeadGears).collect(Collectors.toList());
		clothingGears = Arrays.stream(allClothingGears).collect(Collectors.toList());
		shoesGears = Arrays.stream(allShoesGears).collect(Collectors.toList());
	}

	public void setCurrentSpecialWins(List<SpecialWinCount> specialWins) {
		if (startSpecialWinStats == null) {
			startSpecialWinStats = new HashMap<>();
			specialWins.forEach(sw -> startSpecialWinStats.put(sw.getSpecialWeapon(), sw.getWinCount()));

			var builder = new StringBuilder("## Start special Wins found\n__Start stats__:");
			specialWins.forEach(r -> builder.append("\n- **").append(r.getSpecialWeapon().getName()).append("**: ").append(r.getWinCount()).append(" wins"));
			log.info(builder.toString());
		}

		currentSpecialWinStats = new HashMap<>();
		specialWins.forEach(sw -> currentSpecialWinStats.put(sw.getSpecialWeapon(), sw.getWinCount()));

		var builder = new StringBuilder("## Current Special Wins found\n__Current stats__:");
		specialWins.forEach(r -> builder.append("\n- **").append(r.getSpecialWeapon().getName()).append("**: ").append(r.getWinCount()).append(" wins"));
		log.info(builder.toString());
	}

	private Instant getSlotStartTime(Instant base) {
		return base.atZone(ZoneOffset.UTC)
			.truncatedTo(ChronoUnit.DAYS)
			.withHour(base.atZone(ZoneOffset.UTC).getHour() / 2 * 2)
			.withMinute(0)
			.withSecond(0)
			.withNano(0)
			.toInstant();
	}

	private String getImageEncoded(Image image) {
		try {
			var attemptedImage = imageService.ensureImageIsDownloaded(image);

			if (attemptedImage.isPresent()) {
				log.info("getImageEncoded present");
				try {
					var fileContent = FileUtils.readFileToByteArray(new File(image.getFilePath()));
					return Base64.getEncoder().encodeToString(fileContent);
				} catch (IOException ex) {
					exceptionLogger.logExceptionAsAttachment(log, "getImageEncoded io exception", ex);
					return "IO EXCEPTION DURING IMAGE CONVERSION TO BASE64";
				}
			} else {
				logSender.sendLogs(log, "getImageEncoded not present");
				return "UNABLE TO DOWNLOAD IMAGE TO DRIVE";
			}
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "getImageEncoded global exception", ex);
			return "UNKNOWN EXCEPTION DURING IMAGE CONVERSION TO BASE64";
		}
	}
}

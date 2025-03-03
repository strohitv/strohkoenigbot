package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsModeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
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

@Component
@Log4j2
public class S3StreamStatistics {
	private final ObjectMapper mapper = new ObjectMapper();

	private final Splatoon3VsRotationRepository rotationRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final ImageService imageService;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final DecimalFormat df = new DecimalFormat("#,###,###");

	private final List<Splatoon3VsResult> includedMatches = new ArrayList<>();

	private Double startXZones, startXTower, startXRainmaker, startXClams;
	private Double currentXZones, currentXTower, currentXRainmaker, currentXClams;

	private List<Weapon> startWeaponStats, currentWeaponStats;

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

	public S3StreamStatistics(@Autowired Splatoon3VsRotationRepository vsRotationRepository,
							  @Autowired Splatoon3VsModeRepository vsModeRepository,
							  @Autowired ImageService imageService,
							  @Autowired LogSender logSender,
							  @Autowired ExceptionLogger exceptionLogger) {
		rotationRepository = vsRotationRepository;
		modeRepository = vsModeRepository;
		this.imageService = imageService;
		this.logSender = logSender;
		this.exceptionLogger = exceptionLogger;
		reset();
	}

	public void reset() {
		includedMatches.clear();
		startXZones = startXTower = startXRainmaker = startXClams = currentXZones = currentXTower = currentXRainmaker = currentXClams = null;
		startWeaponStats = currentWeaponStats = null;

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
		if (!includedMatches.isEmpty()) {
			lastUpdate = Instant.now();

			long victoryCount = includedMatches.stream().filter(m -> "win".equalsIgnoreCase(m.getOwnJudgement())).count();
			long defeatCount = includedMatches.stream().filter(m -> "lose".equalsIgnoreCase(m.getOwnJudgement()) || "deemed_lose".equalsIgnoreCase(m.getOwnJudgement())).count();

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

					logSender.sendLogs(log, "number of matches in this rotation: `%d`", allOpenMatchesThisRotation.size());

					logSender.sendLogs(log, "Power of games in this rotation: \n%s", includedMatches.stream()
						.filter(m -> m.getRotation() != null)
						.filter(m -> Objects.equals(m.getRotation().getId(), lastMatch.getRotation().getId()))
						.map(m -> String.format("- power: `%s` - time: `%s` - id: `%s` - rotation-id: `%s`", getPower(m), m.getPlayedTime(), m.getId(), m.getRotation().getId()))
						.reduce((a, b) -> String.format("%s\n%s", a, b)));

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
					logSender.sendLogs(log, "Exception occured during JSON processing! `%s`", ex.getMessage());
					exceptionLogger.logException(log, ex);
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

			var startExpWeapon = getWeaponExp(weaponStatsStart.getStats().getLevel(), weaponStatsStart.getStats().getExpToLevelUp());
			var currentExpWeapon = getWeaponExp(weaponStatsCurrent.getStats().getLevel(), weaponStatsCurrent.getStats().getExpToLevelUp());
			var expWeaponGain = currentExpWeapon - startExpWeapon;

			var expGoal = getExpGoal(weaponStatsCurrent.getStats().getLevel());
			var alreadyOwnedExpRatio = startExpWeapon * 100.0 / expGoal;
			var earnedExpStreamRatio = expWeaponGain * 100.0 / expGoal;
			var remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;

			String mainWeaponUrl = getImageEncoded(player.getWeapon().getImage());
			String subWeaponUrl = getImageEncoded(player.getWeapon().getSubWeapon().getImage());
			String specialWeaponUrl = getImageEncoded(player.getWeapon().getSpecialWeapon().getImage());

			String headGear = getImageEncoded(player.getHeadGear().getOriginalImage());
			String headGearMain = getImageEncoded(player.getHeadGearMainAbility().getImage());
			String headGearSub1 = getImageEncoded(player.getHeadGearSecondaryAbility1().getImage());
			String headGearSub2 = player.getHeadGearSecondaryAbility2() != null ? getImageEncoded(player.getHeadGearSecondaryAbility2().getImage()) : null;
			String headGearSub3 = player.getHeadGearSecondaryAbility3() != null ? getImageEncoded(player.getHeadGearSecondaryAbility3().getImage()) : null;

			String clothesGear = getImageEncoded(player.getClothingGear().getOriginalImage());
			String clothesGearMain = getImageEncoded(player.getClothingMainAbility().getImage());
			String clothesGearSub1 = getImageEncoded(player.getClothingSecondaryAbility1().getImage());
			String clothesGearSub2 = player.getClothingSecondaryAbility2() != null ? getImageEncoded(player.getClothingSecondaryAbility2().getImage()) : null;
			String clothesGearSub3 = player.getClothingSecondaryAbility3() != null ? getImageEncoded(player.getClothingSecondaryAbility3().getImage()) : null;

			String shoesGear = getImageEncoded(player.getShoesGear().getOriginalImage());
			String shoesGearMain = getImageEncoded(player.getShoesMainAbility().getImage());
			String shoesGearSub1 = getImageEncoded(player.getShoesSecondaryAbility1().getImage());
			String shoesGearSub2 = player.getShoesSecondaryAbility2() != null ? getImageEncoded(player.getShoesSecondaryAbility2().getImage()) : null;
			String shoesGearSub3 = player.getShoesSecondaryAbility3() != null ? getImageEncoded(player.getShoesSecondaryAbility3().getImage()) : null;

			var openChangeHidden = openCurrentPower == null
				|| openPreviousPower == null
				|| openCurrentPower.doubleValue() == openPreviousPower.doubleValue();

			logSender.sendLogs(log, String.format("openCurrentPower: `%s`, openPreviousPower: `%s`, openChangeHidden: `%s`, openCurrentPower == null: `%s`, openPreviousPower == null: `%s`, openCurrentPower.doubleValue() == openPreviousPower.doubleValue(): `%s`",
				openCurrentPower != null ? String.format("%.1f", openCurrentPower) : "null",
				openPreviousPower != null ? String.format("%.1f", openPreviousPower) : "null",
				openChangeHidden,
				openCurrentPower == null,
				openPreviousPower == null,
				Objects.equals(openCurrentPower, openPreviousPower)));

			try (var is = this.getClass().getClassLoader().getResourceAsStream("html/s3/onstream-statistics-template.html");) {
				assert is != null;
				currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

				currentHtml = currentHtml
					.replace("{wins}", String.format("%d", victoryCount))
					.replace("{defeats}", String.format("%d", defeatCount))

					.replace("{main-weapon}", String.format("data:image/png;base64,%s", mainWeaponUrl))
					.replace("{sub-weapon}", String.format("data:image/png;base64,%s", subWeaponUrl))
					.replace("{special-weapon}", String.format("data:image/png;base64,%s", specialWeaponUrl))
					.replace("{head}", String.format("data:image/png;base64,%s", headGear))
					.replace("{head-main}", String.format("data:image/png;base64,%s", headGearMain))
					.replace("{head-sub-1}", String.format("data:image/png;base64,%s", headGearSub1))
					.replace("{clothing}", String.format("data:image/png;base64,%s", clothesGear))
					.replace("{clothing-main}", String.format("data:image/png;base64,%s", clothesGearMain))
					.replace("{clothing-sub-1}", String.format("data:image/png;base64,%s", clothesGearSub1))
					.replace("{shoes}", String.format("data:image/png;base64,%s", shoesGear))
					.replace("{shoes-main}", String.format("data:image/png;base64,%s", shoesGearMain))
					.replace("{shoes-sub-1}", String.format("data:image/png;base64,%s", shoesGearSub1))

					.replace("{zones-icon-hidden}", zonesHidden ? "hidden" : "")
					.replace("{tower-icon-hidden}", towerHidden ? "hidden" : "")
					.replace("{rainmaker-icon-hidden}", rainmakerHidden ? "hidden" : "")
					.replace("{clams-icon-hidden}", clamsHidden ? "hidden" : "")
					.replace("{x-stats-hidden}", "X_MATCH".equals(lastMatch.getMode().getApiMode()) ? "" : "hidden")
					.replace("{current-x}", buildCurrentPower(currentPower))
					.replace("{x-change-hidden}", currentPower == null
						|| startPower == null
						|| currentPower.doubleValue() == startPower.doubleValue() ? "hidden" : "")
					.replace("{x-change}", buildPowerDiff(startPower, currentPower))
					.replace("{x-change-color}", getPowerDiffColor(startPower, currentPower))

					.replace("{open-stats-hidden}", lastMatchWasOpenWithFriends ? "" : "hidden")
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

					.replace("{weapon-star-1-hidden}", weaponStatsCurrent.getStats().getLevel() >= 1 ? "" : "hidden")
					.replace("{weapon-star-2-hidden}", weaponStatsCurrent.getStats().getLevel() >= 2 ? "" : "hidden")
					.replace("{weapon-star-3-hidden}", weaponStatsCurrent.getStats().getLevel() >= 3 ? "" : "hidden")
					.replace("{weapon-star-4-hidden}", weaponStatsCurrent.getStats().getLevel() >= 4 ? "" : "hidden")
					.replace("{weapon-star-5-hidden}", weaponStatsCurrent.getStats().getLevel() >= 5 ? "" : "hidden")
					.replace("{exp-hidden}", weaponStatsCurrent.getStats().getLevel() == 5 ? "hidden" : "")
					.replace("{weapon-win-count}", df.format(weaponStatsCurrent.getStats().getWin()).replaceAll(",", " "))

					.replace("{weapon-exp-start}", df.format(startExpWeapon).replaceAll(",", " "))
					.replace("{weapon-exp-gain}", df.format(expWeaponGain).replaceAll(",", " "))
					.replace("{weapon-exp}", df.format(currentExpWeapon).replaceAll(",", " "))

					.replace("{weapon-exp-gain-hidden}", currentExpWeapon == startExpWeapon ? "hidden" : "")

					.replace("{already-owned-exp-ratio}", String.format(Locale.US, "%.2f", alreadyOwnedExpRatio))
					.replace("{earned-exp-stream-ratio}", String.format(Locale.US, "%.2f", earnedExpStreamRatio))
					.replace("{remaining-exp-ratio}", String.format(Locale.US, "%.2f", remainingExpRatio))
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

	private Double getPower(Splatoon3VsResult m) {
		Match previousMatchParsed = null;
		try {
			previousMatchParsed = mapper.readValue(imageService.restoreJson(m.getShortenedJson()), BattleResult.class)
				.getData()
				.getVsHistoryDetail()
				.getBankaraMatch();

			return previousMatchParsed.getBankaraPower() != null ? previousMatchParsed.getBankaraPower().getPower() : null;
		} catch (JsonProcessingException ignored) {
			return null;
		}
	}

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
			case 4:
			case 5: {
				expGoal = 1160000;
				break;
			}
			default:
			case 0: {
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
				currentExp = 1160000;
				break;
			}
			default:
			case 0: {
				currentExp = 5000 - expToLevelUp;
				break;
			}
		}

		return currentExp;
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
		includedMatches.addAll(games.stream().filter(g -> !includedMatches.contains(g)).collect(Collectors.toList()));
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
		var attemptedImage = imageService.ensureImageIsDownloaded(image);

		if (attemptedImage.isPresent()) {
			try {
				var fileContent = FileUtils.readFileToByteArray(new File(image.getFilePath()));
				return Base64.getEncoder().encodeToString(fileContent);
			} catch (IOException ignored) {
				return "EXCEPTION DURING IMAGE CONVERSION TO BASE64";
			}
		} else {
			return "UNABLE TO DOWNLOAD IMAGE TO DRIVE";
		}
	}
}

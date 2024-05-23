package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

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
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class S3StreamStatistics {
	private final Splatoon3VsRotationRepository rotationRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final ImageService imageService;

	private final List<Splatoon3VsResult> includedMatches = new ArrayList<>();

	private Double startXZones, startXTower, startXRainmaker, startXClams;
	private Double currentXZones, currentXTower, currentXRainmaker, currentXClams;

	private final String path = String.format("%s/src/main/resources/html/s3/onstream-statistics-filled.html", Paths.get(".").toAbsolutePath().normalize());

	private String currentHtml = "<!DOCTYPE html>\n" +
		"<html lang=\"en\">\n" +
		"\n" +
		"<head>\n" +
		"\t<meta charset=\"UTF-8\">\n" +
		"\t<meta http-equiv=\"refresh\" content=\"5\">\n" +
		"\t<title>Splatoon 3 statistics</title>\n" +
		"</head>\n" +
		"<body>\n" +
		"</body>\n" +
		"</html>";

	private String finishedHtml = currentHtml;

	public S3StreamStatistics(@Autowired Splatoon3VsRotationRepository vsRotationRepository,
							  @Autowired Splatoon3VsModeRepository vsModeRepository,
							  @Autowired ImageService imageService) {
		rotationRepository = vsRotationRepository;
		modeRepository = vsModeRepository;
		this.imageService = imageService;
		reset();
	}

	private ResourcesDownloader resourcesDownloader;

	@Autowired
	public void setResourcesDownloader(ResourcesDownloader resourcesDownloader) {
		this.resourcesDownloader = resourcesDownloader;
	}

	public String getFinishedHtml() {
		return finishedHtml;
	}

	public void reset() {
		includedMatches.clear();
		startXZones = startXTower = startXRainmaker = startXClams = currentXZones = currentXTower = currentXRainmaker = currentXClams = null;

		InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/s3/afterstream-statistics-template.html");

		try {
			assert is != null;
			currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			finishedHtml = currentHtml;

			FileWriter myWriter = new FileWriter(path);
			myWriter.write(currentHtml);
			myWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void exportHtml() {
		if (includedMatches.size() > 0) {
			long victoryCount = includedMatches.stream().filter(m -> m.getOwnJudgement().equalsIgnoreCase("win")).count();
			long defeatCount = includedMatches.stream().filter(m -> m.getOwnJudgement().equalsIgnoreCase("lose") || m.getOwnJudgement().equalsIgnoreCase("deemed_lose")).count();

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

			if (rotation == null) {
				return;
			}

			Double currentPower;
			Double startPower;
			boolean zonesHidden = true, towerHidden = true, rainmakerHidden = true, clamsHidden = true;
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

			InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/s3/onstream-statistics-template.html");
			try {
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
					.replace("{x-stats-hidden}", lastMatch.getMode().getApiMode().equals("X_MATCH") ? "" : "hidden")
					.replace("{current-x}", buildCurrentX(currentPower))
					.replace("{x-change-hidden}", currentPower == null
						|| startPower == null
						|| currentPower.doubleValue() == startPower.doubleValue() ? "hidden" : "")
					.replace("{x-change}", buildPowerDiff(startPower, currentPower))
					.replace("{x-change-color}", getPowerDiffColor(startPower, currentPower));

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

				FileWriter myWriter = new FileWriter(path);
				myWriter.write(currentHtml);
				myWriter.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private String buildCurrentX(Double currentPower) {
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

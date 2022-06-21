package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;
import tv.strohi.twitch.strohkoenigbot.utils.SplatoonMatchColorComponent;

import java.awt.*;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Statistics {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final List<SplatNetMatchResult> includedMatches = new ArrayList<>();
	private final Map<String, Integer> weaponPaints = new HashMap<>();

	private final String path;

	private String currentHtml = "<!DOCTYPE html>\n" +
			"<html lang=\"en\">\n" +
			"\n" +
			"<head>\n" +
			"\t<meta charset=\"UTF-8\">\n" +
			"\t<meta http-equiv=\"refresh\" content=\"5\">\n" +
			"\t<title>Splatoon 2 statistics</title>\n" +
			"</head>\n" +
			"<body>\n" +
			"</body>\n" +
			"</html>";

	public Statistics() {
		path = String.format("%s\\src\\main\\resources\\html\\template-example.html", Paths.get(".").toAbsolutePath().normalize().toString());
		reset();
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private SplatoonMatchColorComponent splatoonMatchColorComponent;

	@Autowired
	public void setSplatoonMatchColorComponent(SplatoonMatchColorComponent splatoonMatchColorComponent) {
		this.splatoonMatchColorComponent = splatoonMatchColorComponent;
	}

	private ResourcesDownloader resourcesDownloader;

	@Autowired
	public void setResourcesDownloader(ResourcesDownloader resourcesDownloader) {
		this.resourcesDownloader = resourcesDownloader;
	}

	public void reset() {
		includedMatches.clear();
		weaponPaints.clear();
	}

	public void stop() {
		includedMatches.clear();
		weaponPaints.clear();

		InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/template-after-stream.html");

		try {
			assert is != null;
			currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

			FileWriter myWriter = new FileWriter(path);
			myWriter.write(currentHtml);
			myWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getCurrentHtml() {
		return currentHtml;
	}

	public void exportHtml() {
		if (includedMatches.size() > 0) {
			long victoryCount = includedMatches.stream().filter(m -> m.getMy_team_result().getKey().equalsIgnoreCase("victory")).count();
			long defeatCount = includedMatches.stream().filter(m -> m.getMy_team_result().getKey().equalsIgnoreCase("defeat")).count();

			SplatNetMatchResult lastMatch = includedMatches.get(includedMatches.size() - 1);
			SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer player = lastMatch.getPlayer_result().getPlayer();

			String mainWeaponPoints = String.format("%,d", lastMatch.getWeapon_paint_point())
					.replace(DecimalFormatSymbols.getInstance().getGroupingSeparator(), ' ');
			String mainWeaponPointsGain = String.format("%,d", weaponPaints.getOrDefault(lastMatch.getPlayer_result().getPlayer().getWeapon().getId(), 0))
					.replace(DecimalFormatSymbols.getInstance().getGroupingSeparator(), ' ');

			String imageHost = "https://app.splatoon2.nintendo.net";
			String mainWeaponUrl = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getWeapon().getImage()));
			String subWeaponUrl = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getWeapon().getSub().getImage_a()));
			String specialWeaponUrl = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getWeapon().getSpecial().getImage_a()));

			String headGear = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getHead().getImage()));
			String headGearMain = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getHead_skills().getMain().getImage()));
			String headGearSub1 = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getHead_skills().getSubs()[0].getImage()));
			String headGearSub2 = player.getHead_skills().getSubs().length > 1 && player.getHead_skills().getSubs()[1] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getHead_skills().getSubs()[1].getImage())) : null;
			String headGearSub3 = player.getHead_skills().getSubs().length > 2 && player.getHead_skills().getSubs()[2] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getHead_skills().getSubs()[2].getImage())) : null;

			String clothesGear = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getClothes().getImage()));
			String clothesGearMain = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getClothes_skills().getMain().getImage()));
			String clothesGearSub1 = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[0].getImage()));
			String clothesGearSub2 = player.getClothes_skills().getSubs().length > 1 && player.getClothes_skills().getSubs()[1] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[1].getImage())) : null;
			String clothesGearSub3 = player.getClothes_skills().getSubs().length > 2 && player.getClothes_skills().getSubs()[2] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[2].getImage())) : null;

			String shoesGear = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getShoes().getImage()));
			String shoesGearMain = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getShoes_skills().getMain().getImage()));
			String shoesGearSub1 = resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[0].getImage()));
			String shoesGearSub2 = player.getShoes_skills().getSubs().length > 1 && player.getShoes_skills().getSubs()[1] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[1].getImage())) : null;
			String shoesGearSub3 = player.getShoes_skills().getSubs().length > 2 && player.getShoes_skills().getSubs()[2] != null ? resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[2].getImage())) : null;

			String possiblePowerHidden = "hidden";
			String possiblePowerGain = "";
			String possiblePowerLoss = "";

			Configuration woomyDxDir = configurationRepository.findByConfigName("woomyDxDir").stream().findFirst().orElse(null);
			if (woomyDxDir != null) {
				Path path = Paths.get(woomyDxDir.getConfigValue());

				logger.info("woomyDxDir: {}", woomyDxDir.getConfigValue());
				logger.info("woomyDxDir via paths.get: {}", path.toString());

				if (Files.exists(path)) {
					try {
						logger.info("win path: {}", path.resolve("win.txt").toString());
						logger.info("lose path: {}", path.resolve("lose.txt").toString());

						InputStream isPowerGain = new FileInputStream(path.resolve("win.txt").toString());
						InputStream isPowerLoss = new FileInputStream(path.resolve("lose.txt").toString());

						String possiblePowerGainRead = new String(isPowerGain.readAllBytes(), StandardCharsets.UTF_8);
						String possiblePowerLossRead = new String(isPowerLoss.readAllBytes(), StandardCharsets.UTF_8);

						if (!possiblePowerGainRead.isBlank() && !possiblePowerLossRead.isBlank()) {
							logger.info("Setting gain to {} and loss to {}", possiblePowerGainRead.trim(), possiblePowerLossRead.trim());
							possiblePowerGain = possiblePowerGainRead.trim();
							possiblePowerLoss = possiblePowerLossRead.trim();

							possiblePowerHidden = "";
						} else {
							logger.info("possible power gain and loss are both blank, not setting any powers");
						}
					} catch (IOException e) {
						logger.error(e);
					}
				} else {
					logger.error("could not open woomy dx files");
				}
			} else {
				logger.error("woomy dx configuration property does not exist");
			}

			InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/template.html");

			Color bgColor = splatoonMatchColorComponent.getBackgroundColor();
			Color greenColor = splatoonMatchColorComponent.getGreenColor();
			Color redColor = splatoonMatchColorComponent.getRedColor();

			try {
				assert is != null;
				currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

				currentHtml = currentHtml
						.replace("{wins}", String.format("%d", victoryCount))
						.replace("{defeats}", String.format("%d", defeatCount))
						.replace("{main-weapon-points}", mainWeaponPoints)
						.replace("{main-weapon-points-gain}", mainWeaponPointsGain)
						.replace("{main-weapon}", mainWeaponUrl)
						.replace("{sub-weapon}", subWeaponUrl)
						.replace("{special-weapon}", specialWeaponUrl)
						.replace("{head}", headGear)
						.replace("{head-main}", headGearMain)
						.replace("{head-sub-1}", headGearSub1)
						.replace("{clothing}", clothesGear)
						.replace("{clothing-main}", clothesGearMain)
						.replace("{clothing-sub-1}", clothesGearSub1)
						.replace("{shoes}", shoesGear)
						.replace("{shoes-main}", shoesGearMain)
						.replace("{shoes-sub-1}", shoesGearSub1)
						.replace("{possible-power-change-hidden}", possiblePowerHidden)
						.replace("{possible-x-power-gain}", possiblePowerGain)
						.replace("{possible-x-power-loss}", possiblePowerLoss)
						.replace("bgRed", String.format("%d", bgColor.getRed()))
						.replace("bgGreen", String.format("%d", bgColor.getGreen()))
						.replace("bgBlue", String.format("%d", bgColor.getBlue()))
						.replace("greenRed", String.format("%d", greenColor.getRed()))
						.replace("greenGreen", String.format("%d", greenColor.getGreen()))
						.replace("greenBlue", String.format("%d", greenColor.getBlue()))
						.replace("redRed", String.format("%d", redColor.getRed()))
						.replace("redGreen", String.format("%d", redColor.getGreen()))
						.replace("redBlue", String.format("%d", redColor.getBlue()));

				if (headGearSub2 != null) {
					currentHtml = currentHtml.replace("{head-sub-2}", headGearSub2)
							.replace("{head-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{head-sub-2}", "")
							.replace("{head-sub2-hidden}", "hidden");
				}

				if (headGearSub3 != null) {
					currentHtml = currentHtml.replace("{head-sub-3}", headGearSub3)
							.replace("{head-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{head-sub-3}", "")
							.replace("{head-sub3-hidden}", "hidden");
				}

				if (clothesGearSub2 != null) {
					currentHtml = currentHtml.replace("{clothing-sub-2}", clothesGearSub2)
							.replace("{clothing-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{clothing-sub-2}", "")
							.replace("{clothing-sub2-hidden}", "hidden");
				}

				if (clothesGearSub3 != null) {
					currentHtml = currentHtml.replace("{clothing-sub-3}", clothesGearSub3)
							.replace("{clothing-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{clothing-sub-3}", "")
							.replace("{clothing-sub3-hidden}", "hidden");
				}

				if (shoesGearSub2 != null) {
					currentHtml = currentHtml.replace("{shoes-sub-2}", shoesGearSub2)
							.replace("{shoes-sub2-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{shoes-sub-2}", "")
							.replace("{shoes-sub2-hidden}", "hidden");
				}

				if (shoesGearSub3 != null) {
					currentHtml = currentHtml.replace("{shoes-sub-3}", shoesGearSub3)
							.replace("{shoes-sub3-hidden}", "");
				} else {
					currentHtml = currentHtml.replace("{shoes-sub-3}", "")
							.replace("{shoes-sub3-hidden}", "hidden");
				}

				FileWriter myWriter = new FileWriter(path);
				myWriter.write(currentHtml);
				myWriter.close();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
	}

	public void addMatches(List<SplatNetMatchResult> matches) {
		if (matches.size() > 0) {
			includedMatches.addAll(matches);

			for (SplatNetMatchResult result : matches) {
				String weaponId = result.getPlayer_result().getPlayer().getWeapon().getId();

				int newPaint = weaponPaints.getOrDefault(weaponId, 0);
				newPaint += result.getPlayer_result().getGame_paint_point();

				weaponPaints.put(weaponId, newPaint);
			}
		}
	}
}

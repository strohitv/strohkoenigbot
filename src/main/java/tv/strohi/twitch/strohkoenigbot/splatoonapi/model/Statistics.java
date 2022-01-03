package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Statistics {
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

	private final List<SplatNetMatchResult> includedMatches = new ArrayList<>();
	private final Map<String, Integer> weaponPaints = new HashMap<>();

	private final String path;

	public Statistics(String path) {
		this.path = path;
		reset();
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
			String mainWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getImage());
			String subWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getSub().getImage_a());
			String specialWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getSpecial().getImage_a());

			String headGear = String.format("%s%s", imageHost, player.getHead().getImage());
			String headGearMain = String.format("%s%s", imageHost, player.getHead_skills().getMain().getImage());
			String headGearSub1 = String.format("%s%s", imageHost, player.getHead_skills().getSubs()[0].getImage());
			String headGearSub2 = player.getHead_skills().getSubs().length > 1 && player.getHead_skills().getSubs()[1] != null ? String.format("%s%s", imageHost, player.getHead_skills().getSubs()[1].getImage()) : null;
			String headGearSub3 = player.getHead_skills().getSubs().length > 2 && player.getHead_skills().getSubs()[2] != null ? String.format("%s%s", imageHost, player.getHead_skills().getSubs()[2].getImage()) : null;

			String clothesGear = String.format("%s%s", imageHost, player.getClothes().getImage());
			String clothesGearMain = String.format("%s%s", imageHost, player.getClothes_skills().getMain().getImage());
			String clothesGearSub1 = String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[0].getImage());
			String clothesGearSub2 = player.getClothes_skills().getSubs().length > 1 && player.getClothes_skills().getSubs()[1] != null ? String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[1].getImage()) : null;
			String clothesGearSub3 = player.getClothes_skills().getSubs().length > 2 && player.getClothes_skills().getSubs()[2] != null ? String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[2].getImage()) : null;

			String shoesGear = String.format("%s%s", imageHost, player.getShoes().getImage());
			String shoesGearMain = String.format("%s%s", imageHost, player.getShoes_skills().getMain().getImage());
			String shoesGearSub1 = String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[0].getImage());
			String shoesGearSub2 = player.getShoes_skills().getSubs().length > 1 && player.getShoes_skills().getSubs()[1] != null ? String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[1].getImage()) : null;
			String shoesGearSub3 = player.getShoes_skills().getSubs().length > 2 && player.getShoes_skills().getSubs()[2] != null ? String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[2].getImage()) : null;

			String possiblePowerHidden  = "hidden";
			String possiblePowerGain = "";
			String possiblePowerLoss = "";

			Path htmlFilePath = Paths.get(path).getParent();
			try {
				InputStream isPowerGain = new FileInputStream(Paths.get(htmlFilePath.toString(),"/snowpoke/win.txt").toString());
				InputStream isPowerLoss = new FileInputStream(Paths.get(htmlFilePath.toString(),"/snowpoke/lose.txt").toString());

				String possiblePowerGainRead = new String(isPowerGain.readAllBytes(), StandardCharsets.UTF_8);
				String possiblePowerLossRead = new String(isPowerLoss.readAllBytes(), StandardCharsets.UTF_8);

				if (!possiblePowerGainRead.isBlank() && !possiblePowerLossRead.isBlank()) {
					possiblePowerGain = possiblePowerGainRead.trim();
					possiblePowerLoss = possiblePowerLossRead.trim();

					possiblePowerHidden = "";
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/template.html");

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
						.replace("{possible-x-power-loss}", possiblePowerLoss);

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

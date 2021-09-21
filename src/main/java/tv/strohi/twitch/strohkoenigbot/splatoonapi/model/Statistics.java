package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Statistics {
	private final List<SplatoonMatchResultsCollection.SplatoonMatchResult> includedMatches = new ArrayList<>();
	private final String imageHost = "https://app.splatoon2.nintendo.net";

	private boolean dirty;

	private String path;

	public Statistics(String path) {
		this.path = path;
		reset();
	}

	public void reset() {
		includedMatches.clear();
	}

	public void exportHtml() {
		if (dirty && includedMatches.size() > 0) {
			long victoryCount = includedMatches.stream().filter(m -> m.getMy_team_result().getKey().equalsIgnoreCase("victory")).count();
			long defeatCount = includedMatches.stream().filter(m -> m.getMy_team_result().getKey().equalsIgnoreCase("defeat")).count();

			SplatoonMatchResultsCollection.SplatoonMatchResult lastMatch = includedMatches.get(includedMatches.size() - 1);
			SplatoonMatchResultsCollection.SplatoonMatchResult.SplatoonPlayerResult.SplatoonPlayer player = lastMatch.getPlayer_result().getPlayer();

			String mainWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getImage());
			String subWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getSub().getImage_a());
			String specialWeaponUrl = String.format("%s%s", imageHost, player.getWeapon().getSpecial().getImage_a());

			String headGear = String.format("%s%s", imageHost, player.getHead().getImage());
			String headGearMain = String.format("%s%s", imageHost, player.getHead_skills().getMain().getImage());
			String headGearSub1 = String.format("%s%s", imageHost, player.getHead_skills().getSubs()[0].getImage());
			String headGearSub2 = player.getHead_skills().getSubs().length > 1 ? String.format("%s%s", imageHost, player.getHead_skills().getSubs()[1].getImage()) : null;
			String headGearSub3 = player.getHead_skills().getSubs().length > 2 ? String.format("%s%s", imageHost, player.getHead_skills().getSubs()[2].getImage()) : null;

			String clothesGear = String.format("%s%s", imageHost, player.getClothes().getImage());
			String clothesGearMain = String.format("%s%s", imageHost, player.getClothes_skills().getMain().getImage());
			String clothesGearSub1 = String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[0].getImage());
			String clothesGearSub2 = player.getClothes_skills().getSubs().length > 1 ? String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[1].getImage()) : null;
			String clothesGearSub3 = player.getClothes_skills().getSubs().length > 2 ? String.format("%s%s", imageHost, player.getClothes_skills().getSubs()[2].getImage()) : null;

			String shoesGear = String.format("%s%s", imageHost, player.getShoes().getImage());
			String shoesGearMain = String.format("%s%s", imageHost, player.getShoes_skills().getMain().getImage());
			String shoesGearSub1 = String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[0].getImage());
			String shoesGearSub2 = player.getShoes_skills().getSubs().length > 1 ? String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[1].getImage()) : null;
			String shoesGearSub3 = player.getShoes_skills().getSubs().length > 2 ? String.format("%s%s", imageHost, player.getShoes_skills().getSubs()[2].getImage()) : null;

			InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/template.html");

			try {
				assert is != null;
				String fileToWrite = new String(is.readAllBytes(), StandardCharsets.UTF_8);

				fileToWrite = fileToWrite
						.replace("{wins}", String.format("%d", victoryCount))
						.replace("{defeats}", String.format("%d", defeatCount))
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
						.replace("{shoes-sub-1}", shoesGearSub1);

				if (headGearSub2 != null) {
					fileToWrite = fileToWrite.replace("{head-sub-2}", headGearSub2)
							.replace("{head-sub2-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{head-sub-2}", "")
							.replace("{head-sub2-hidden}", "hidden");
				}

				if (headGearSub3 != null) {
					fileToWrite = fileToWrite.replace("{head-sub-3}", headGearSub3)
							.replace("{head-sub3-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{head-sub-3}", "")
							.replace("{head-sub3-hidden}", "hidden");
				}

				if (clothesGearSub2 != null) {
					fileToWrite = fileToWrite.replace("{clothing-sub-2}", clothesGearSub2)
							.replace("{clothing-sub2-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{clothing-sub-2}", "")
							.replace("{clothing-sub2-hidden}", "hidden");
				}

				if (clothesGearSub3 != null) {
					fileToWrite = fileToWrite.replace("{clothing-sub-3}", clothesGearSub3)
							.replace("{clothing-sub3-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{clothing-sub-3}", "")
							.replace("{clothing-sub3-hidden}", "hidden");
				}

				if (shoesGearSub2 != null) {
					fileToWrite = fileToWrite.replace("{shoes-sub-2}", shoesGearSub2)
							.replace("{shoes-sub2-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{shoes-sub-2}", "")
							.replace("{shoes-sub2-hidden}", "hidden");
				}

				if (shoesGearSub3 != null) {
					fileToWrite = fileToWrite.replace("{shoes-sub-3}", shoesGearSub3)
							.replace("{shoes-sub3-hidden}", "");
				} else {
					fileToWrite = fileToWrite.replace("{shoes-sub-3}", "")
							.replace("{shoes-sub3-hidden}", "hidden");
				}

				FileWriter myWriter = new FileWriter(path);
				myWriter.write(fileToWrite);
				myWriter.close();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

			dirty = false;
		}
	}

	public void addMatches(List<SplatoonMatchResultsCollection.SplatoonMatchResult> matches) {
		if (matches.size() > 0) {
			dirty = true;
			includedMatches.addAll(matches);
		}
	}
}

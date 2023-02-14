package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
public class DailyStatsSaveModel {
	private List<String> ignoredBrands = new ArrayList<>(List.of("amiibo", "Cuttlegear", "Grizzco"));
	private List<String> doneBrands = new ArrayList<>(List.of());
	private Map<String, Integer> previousStarCount = new HashMap<>();

	private List<String> ignoredModes = new ArrayList<>(List.of("Tricolor Turf War (Defender)", "Tricolor Turf War (Attacker)"));
	private Map<String, Integer> previousModeWinCount = new HashMap<>();
	private Map<String, Integer> previousSpecialWeaponWinCount = new HashMap<>();
	private Map<String, Integer> previousWeaponStarsCount = new HashMap<>();

	private List<String> ignoredSalmonRunBosses = new ArrayList<>(List.of("Goldie", "Griller", "Mudmouth"));
	private Map<String, Integer> previousSalmonRunBossDefeatCount = new HashMap<>();
}

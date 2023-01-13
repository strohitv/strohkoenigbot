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
public class DailyGearStatsSaveModel {
	private List<String> ignoredBrands = new ArrayList<>(List.of("amiibo", "Cuttlegear", "Grizzco"));
	private List<String> doneBrands = new ArrayList<>(List.of("SquidForce", "Skalop"));
	private Map<String, Integer> previousStarCount = new HashMap<>();
}

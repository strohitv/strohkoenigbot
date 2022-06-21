package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import lombok.Getter;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Getter
public class TextFilters {
	private final Map<SplatoonStage, Pattern> stageExcludeFilters = new HashMap<>();
	private final Map<SplatoonStage, Pattern> stageIncludeFilters = new HashMap<>();
	private final Map<RuleFilter, Pattern> ruleFilters = new HashMap<>();
	private final Map<DayFilter, Pattern> dayWithTimeFilters = new HashMap<>();
	private final Map<DayFilter, Pattern> dayWithoutTimeFilters = new HashMap<>();
	private final Map<SalmonRunWeapon, Pattern> salmonRunWeaponExcludeFilters = new HashMap<>();
	private final Map<SalmonRunWeapon, Pattern> salmonRunWeaponIncludeFilters = new HashMap<>();
	private final Map<SalmonRunStage, Pattern> salmonRunStageExcludeFilters = new HashMap<>();
	private final Map<SalmonRunStage, Pattern> salmonRunStageIncludeFilters = new HashMap<>();
	private final Map<SalmonRunRandomFilter, Pattern> salmonRunRandomFilters = new HashMap<>();
	private final Map<GearSlotFilter, Pattern> gearSlotFilters = new HashMap<>();

	private final Pattern timeFilter;

	public TextFilters() {
		String timeFilterPattern = "((?<=\\s)|^)([0-2][0-3]|[0-1]?[0-9])\\s*-\\s*([0-2][0-3]|[0-1]?[0-9])((?=\\s)|$)";
		timeFilter = Pattern.compile(timeFilterPattern);

		for (SplatoonStage stage : SplatoonStage.All) {
			StringBuilder stageConcatenateBuilder = new StringBuilder(stage.getName().toLowerCase());
			Arrays.stream(stage.getAltNames()).forEach(name -> stageConcatenateBuilder.append("|").append(name.toLowerCase()));
			String stageNames = stageConcatenateBuilder.toString();

			String excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", stageNames);
			stageExcludeFilters.put(stage, Pattern.compile(excludeRegex));

			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", stageNames);
			stageIncludeFilters.put(stage, Pattern.compile(includeRegex));
		}

		for (RuleFilter ruleFilter : RuleFilter.RankedModes) {
			StringBuilder ruleConcatenateBuilder = new StringBuilder(ruleFilter.getName().toLowerCase());
			Arrays.stream(ruleFilter.getAltNames()).forEach(name -> ruleConcatenateBuilder.append("|").append(name.toLowerCase()));
			String ruleNames = ruleConcatenateBuilder.toString();

			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", ruleNames);
			ruleFilters.put(ruleFilter, Pattern.compile(includeRegex));
		}

		for (DayFilter dayFilter : DayFilter.All) {
			StringBuilder dayFilterConcatenateBuilder = new StringBuilder(dayFilter.getName().toLowerCase());
			Arrays.stream(dayFilter.getAltNames()).forEach(name -> dayFilterConcatenateBuilder.append("|").append(name.toLowerCase()));
			String dayNames = dayFilterConcatenateBuilder.toString();

			String withTimeRegex = String.format("((?<=\\s)|^)(%s)\\s+%s", dayNames, timeFilterPattern);
			dayWithTimeFilters.put(dayFilter, Pattern.compile(withTimeRegex));

			String withoutTimeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", dayNames);
			dayWithoutTimeFilters.put(dayFilter, Pattern.compile(withoutTimeRegex));
		}

		for (SalmonRunWeapon weapon : SalmonRunWeapon.All) {
			StringBuilder weaponConcatenateBuilder = new StringBuilder(weapon.getName().toLowerCase());
			Arrays.stream(weapon.getAltNames()).forEach(name -> weaponConcatenateBuilder.append("|").append(name.toLowerCase()));
			String weaponNames = weaponConcatenateBuilder.toString();

			String excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", weaponNames);
			salmonRunWeaponExcludeFilters.put(weapon, Pattern.compile(excludeRegex));

			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", weaponNames);
			salmonRunWeaponIncludeFilters.put(weapon, Pattern.compile(includeRegex));
		}

		for (SalmonRunStage stage : SalmonRunStage.All) {
			StringBuilder stageConcatenateBuilder = new StringBuilder(stage.getName().toLowerCase());
			Arrays.stream(stage.getAltNames()).forEach(name -> stageConcatenateBuilder.append("|").append(name.toLowerCase()));
			String stageNames = stageConcatenateBuilder.toString();

			String excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", stageNames);
			salmonRunStageExcludeFilters.put(stage, Pattern.compile(excludeRegex));

			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", stageNames);
			salmonRunStageIncludeFilters.put(stage, Pattern.compile(includeRegex));
		}

		for (GearSlotFilter gearSlotFilter : GearSlotFilter.All) {
			StringBuilder gearSlotConcatenateBuilder = new StringBuilder(gearSlotFilter.getName().toLowerCase());
			Arrays.stream(gearSlotFilter.getAltNames()).forEach(name -> gearSlotConcatenateBuilder.append("|").append(name.toLowerCase()));
			String gearSlotNames = gearSlotConcatenateBuilder.toString();

//			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", gearSlotNames);
			String includeRegex = String.format("(%s)", gearSlotNames);
			gearSlotFilters.put(gearSlotFilter, Pattern.compile(includeRegex));
		}

		for (SalmonRunRandomFilter salmonRunRandomFilter : SalmonRunRandomFilter.All) {
			StringBuilder randomWeaponConcatenateBuilder = new StringBuilder(salmonRunRandomFilter.getName().toLowerCase());
			Arrays.stream(salmonRunRandomFilter.getAltNames()).forEach(name -> randomWeaponConcatenateBuilder.append("|").append(name.toLowerCase()));
			String randomFilters = randomWeaponConcatenateBuilder.toString();

//			String includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", randomFilters);
			String includeRegex = String.format("(%s)", randomFilters);
			salmonRunRandomFilters.put(salmonRunRandomFilter, Pattern.compile(includeRegex));
		}
	}
}

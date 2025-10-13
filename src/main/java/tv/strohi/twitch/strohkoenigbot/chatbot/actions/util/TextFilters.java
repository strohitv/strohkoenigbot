package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import lombok.Getter;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.*;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Getter
public class TextFilters {
	private final Map<Splatoon2Stage, Pattern> s2stageExcludeFilters = new HashMap<>();
	private final Map<Splatoon2Stage, Pattern> s2stageIncludeFilters = new HashMap<>();
	private final Map<Splatoon3Stage, Pattern> s3stageExcludeFilters = new HashMap<>();
	private final Map<Splatoon3Stage, Pattern> s3stageIncludeFilters = new HashMap<>();
	private final Map<RuleFilter, Pattern> rankedRuleFilters = new HashMap<>();
	private final Map<RuleFilter, Pattern> allRuleFilters = new HashMap<>();
	private final Map<DayFilter, Pattern> dayWithTimeFilters = new HashMap<>();
	private final Map<DayFilter, Pattern> dayWithoutTimeFilters = new HashMap<>();
	private final Map<SalmonRunWeapon, Pattern> salmonRunWeaponExcludeFilters = new HashMap<>();
	private final Map<SalmonRunWeapon, Pattern> salmonRunWeaponIncludeFilters = new HashMap<>();
	private final Map<SalmonRunStage, Pattern> salmonRunStageExcludeFilters = new HashMap<>();
	private final Map<SalmonRunStage, Pattern> salmonRunStageIncludeFilters = new HashMap<>();
	private final Map<SalmonRunRandomFilter, Pattern> salmonRunRandomFilters = new HashMap<>();
	private final Map<GearSlotFilter, Pattern> gearSlotFilters = new HashMap<>();

	private final Pattern timeFilter;
	private final Pattern utcOffsetFilter;
	private final Map<String, Pattern> timezoneFilters = new HashMap<>();

	public TextFilters() {
		var timeFilterPattern = "((?<=\\s)|^)([0-2][0-3]|[0-1]?[0-9])\\s*-\\s*([0-2][0-3]|[0-1]?[0-9])((?=\\s)|$)";
		timeFilter = Pattern.compile(timeFilterPattern);

		var utcOffsetFilterPattern = "((?<=\\s)|^)([+\\-][0-1]?[0-9](:[0-5][0-9])?)((?=\\s)|$)";
		utcOffsetFilter = Pattern.compile(utcOffsetFilterPattern);

//		var timezoneFilterPattern = "((?<=\\s)|^)([A-Za-z]{3,20}(/[A-Z0-9a-z_\\-]{3,20})?)((?=\\s)|$)";
//		timezoneFilter = Pattern.compile(timezoneFilterPattern);

		for (var timezone : ZoneId.getAvailableZoneIds()) {
			timezoneFilters.put(timezone, Pattern.compile(String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", Pattern.quote(timezone)), Pattern.CASE_INSENSITIVE));
		}

		for (var stage : Splatoon2Stage.All) {
			StringBuilder stageConcatenateBuilder = new StringBuilder(stage.getName().toLowerCase());
			Arrays.stream(stage.getAltNames()).forEach(name -> stageConcatenateBuilder.append("|").append(name.toLowerCase()));
			var stageNames = stageConcatenateBuilder.toString();

			var excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", stageNames);
			s2stageExcludeFilters.put(stage, Pattern.compile(excludeRegex));

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", stageNames);
			s2stageIncludeFilters.put(stage, Pattern.compile(includeRegex));
		}

		for (var stage : Splatoon3Stage.All) {
			StringBuilder stageConcatenateBuilder = new StringBuilder(stage.getName().toLowerCase());
			Arrays.stream(stage.getAltNames()).forEach(name -> stageConcatenateBuilder.append("|").append(name.toLowerCase()));
			var stageNames = stageConcatenateBuilder.toString();

			var excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", stageNames);
			s3stageExcludeFilters.put(stage, Pattern.compile(excludeRegex));

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", stageNames);
			s3stageIncludeFilters.put(stage, Pattern.compile(includeRegex));
		}

		for (RuleFilter ruleFilter : RuleFilter.RankedModes) {
			StringBuilder ruleConcatenateBuilder = new StringBuilder(ruleFilter.getName().toLowerCase());
			Arrays.stream(ruleFilter.getAltNames()).forEach(name -> ruleConcatenateBuilder.append("|").append(name.toLowerCase()));
			var ruleNames = ruleConcatenateBuilder.toString();

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", ruleNames);
			rankedRuleFilters.put(ruleFilter, Pattern.compile(includeRegex));
		}

		for (RuleFilter ruleFilter : RuleFilter.TwoTeamModes) {
			StringBuilder ruleConcatenateBuilder = new StringBuilder(ruleFilter.getName().toLowerCase());
			Arrays.stream(ruleFilter.getAltNames()).forEach(name -> ruleConcatenateBuilder.append("|").append(name.toLowerCase()));
			var ruleNames = ruleConcatenateBuilder.toString();

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", ruleNames);
			allRuleFilters.put(ruleFilter, Pattern.compile(includeRegex));
		}

		for (DayFilter dayFilter : DayFilter.All) {
			StringBuilder dayFilterConcatenateBuilder = new StringBuilder(dayFilter.getName().toLowerCase());
			Arrays.stream(dayFilter.getAltNames()).forEach(name -> dayFilterConcatenateBuilder.append("|").append(name.toLowerCase()));
			var dayNames = dayFilterConcatenateBuilder.toString();

			var withTimeRegex = String.format("((?<=\\s)|^)(%s)\\s+%s", dayNames, timeFilterPattern);
			dayWithTimeFilters.put(dayFilter, Pattern.compile(withTimeRegex));

			var withoutTimeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", dayNames);
			dayWithoutTimeFilters.put(dayFilter, Pattern.compile(withoutTimeRegex));
		}

		for (SalmonRunWeapon weapon : SalmonRunWeapon.All) {
			StringBuilder weaponConcatenateBuilder = new StringBuilder(weapon.getName().toLowerCase());
			Arrays.stream(weapon.getAltNames()).forEach(name -> weaponConcatenateBuilder.append("|").append(name.toLowerCase()));
			var weaponNames = weaponConcatenateBuilder.toString();

			var excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", weaponNames);
			salmonRunWeaponExcludeFilters.put(weapon, Pattern.compile(excludeRegex));

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", weaponNames);
			salmonRunWeaponIncludeFilters.put(weapon, Pattern.compile(includeRegex));
		}

		for (SalmonRunStage stage : SalmonRunStage.All) {
			StringBuilder stageConcatenateBuilder = new StringBuilder(stage.getName().toLowerCase());
			Arrays.stream(stage.getAltNames()).forEach(name -> stageConcatenateBuilder.append("|").append(name.toLowerCase()));
			var stageNames = stageConcatenateBuilder.toString();

			var excludeRegex = String.format("((?<=\\s)|^)not\\s+(%s)((?=\\s)|$)", stageNames);
			salmonRunStageExcludeFilters.put(stage, Pattern.compile(excludeRegex));

			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", stageNames);
			salmonRunStageIncludeFilters.put(stage, Pattern.compile(includeRegex));
		}

		for (GearSlotFilter gearSlotFilter : GearSlotFilter.All) {
			StringBuilder gearSlotConcatenateBuilder = new StringBuilder(gearSlotFilter.getName().toLowerCase());
			Arrays.stream(gearSlotFilter.getAltNames()).forEach(name -> gearSlotConcatenateBuilder.append("|").append(name.toLowerCase()));
			var gearSlotNames = gearSlotConcatenateBuilder.toString();

//			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", gearSlotNames);
			var includeRegex = String.format("(%s)", gearSlotNames);
			gearSlotFilters.put(gearSlotFilter, Pattern.compile(includeRegex));
		}

		for (SalmonRunRandomFilter salmonRunRandomFilter : SalmonRunRandomFilter.All) {
			StringBuilder randomWeaponConcatenateBuilder = new StringBuilder(salmonRunRandomFilter.getName().toLowerCase());
			Arrays.stream(salmonRunRandomFilter.getAltNames()).forEach(name -> randomWeaponConcatenateBuilder.append("|").append(name.toLowerCase()));
			var randomFilters = randomWeaponConcatenateBuilder.toString();

//			var includeRegex = String.format("((?<=\\s)|^)(%s)((?=\\s)|$)", randomFilters);
			var includeRegex = String.format("(%s)", randomFilters);
			salmonRunRandomFilters.put(salmonRunRandomFilter, Pattern.compile(includeRegex));
		}
	}
}

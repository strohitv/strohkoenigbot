package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3NewGearChecker;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.GearPower;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class S3FindBestGearAction extends ChatAction {
	private final S3NewGearChecker gearChecker;

	private Instant lastUsed = Instant.now().truncatedTo(ChronoUnit.DAYS);

	public S3FindBestGearAction(S3NewGearChecker gearChecker) {
		this.gearChecker = gearChecker;

		var abilities = new HashMap<String, String>();
		abilities.put("ism", "Ink Saver (Main)");
		abilities.put("iss", "Ink Saver (Sub)");
		abilities.put("rec", "Ink Recovery Up");
		abilities.put("rsu", "Run Speed Up");
		abilities.put("ssu", "Swim Speed Up");
		abilities.put("scu", "Special Charge Up");
		abilities.put("sps", "Special Saver");
		abilities.put("sppu", "Special Power Up");
		abilities.put("qr", "Quick Respawn");
		abilities.put("qsj", "Quick Super Jump");
		abilities.put("supu", "Sub Power Up");
		abilities.put("res", "Ink Resistance Up");
		abilities.put("sru", "Sub Resistance Up");
		abilities.put("ia", "Intensify Action");

		genericAbilities = Map.copyOf(abilities);
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage, TriggerReason.DiscordMessage, TriggerReason.DiscordPrivateMessage);
	}

	private final Map<String, String> headAbilities = Map.of("cbk", "Comeback", "lde", "Last-Ditch Effort", "ten", "Tenacity", "og", "Opening Gambit");
	private final Map<String, String> clothesAbilities = Map.of("nin", "Ninja Squid", "hau", "Haunt", "ti", "Thermal Ink", "rp", "Respawn Punisher", "ad", "Ability Doubler");
	private final Map<String, String> shoesAbilities = Map.of("sj", "Stealth Jump", "os", "Object Shredder", "dr", "Drop Roller");
	private final Map<String, String> genericAbilities;

	private final int exclusivePoints = 150;
	private final int mainPoints = 100;
	private final int subPoints = 30;
	private final int otherPoints = 1;

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (!message.startsWith("!fbg")) {
			return;
		}

		var preferGrind = message.contains("grind");
		var all = message.contains("all");

		if (lastUsed.isBefore(Instant.now().minus(30, ChronoUnit.MINUTES))) {
			gearChecker.checkForNewGearInSplatNetShop(true);
			lastUsed = Instant.now();
		}

		if (gearChecker.getAllOwnedGear().isEmpty()) {
			args.getReplySender().send("ERROR: I could not find gear via SplatNet!");
			return;
		}

		var allFoundFilters = new ArrayList<String>();

		var pattern = Pattern.compile(createFilterRegex(false));
		var matcher = pattern.matcher(message);
		while (matcher.find()) {
			allFoundFilters.add(matcher.group());
		}

		var validAbilityFilters = allFoundFilters.stream()
			.map(String::trim)
			.filter(t -> !t.isBlank())
			.filter(t -> t.matches(createFilterRegex()))
			.collect(Collectors.toList());

		if (validAbilityFilters.isEmpty()) {
			args.getReplySender().send("ERROR: no filters provided, just equip anything.");
			return;
		}

		var mains = validAbilityFilters.stream()
			.filter(a -> headAbilities.containsKey(a)
				|| clothesAbilities.containsKey(a)
				|| shoesAbilities.containsKey(a)
				|| (!a.startsWith("0.") && !a.startsWith("0m")))
			.map(a -> a.replaceAll("(([0-3]\\s*\\.\\s*[0-9])|([0-3]\\s*m\\s*[0-9]\\s*s))", "").trim())
			.map(this::mapToFullAbilityName)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());

		var mainFilters = new HashMap<String, Integer>();

		for (var filter : validAbilityFilters) {
			if (filter.startsWith("0.") || filter.startsWith("0m")) {
				continue;
			}

			if (filter.matches("^(([0-3]\\s*\\.\\s*[0-9])|([0-3]\\s*m\\s*[0-9]\\s*s))\\s*.*$")) {
				// generic ability
				var mainCount = Integer.parseInt(filter.split("[.m]")[0]);
				var mainAbilityName = mapToFullAbilityName(filter.replaceAll("([0-3]\\s*\\.\\s*[0-9])|([0-3]\\s*m\\s*[0-9]\\s*s)", "").trim());

				mainFilters.putIfAbsent(mainAbilityName, 0);
				mainFilters.put(mainAbilityName, mainFilters.get(mainAbilityName) + mainCount);
			} else {
				// exclusive ability
				mainFilters.putIfAbsent(mapToFullAbilityName(filter), 1);
			}
		}

		var subFilters = new HashMap<String, Integer>();
		var validSubFilters = validAbilityFilters.stream()
			.filter(f -> f.matches("(([0-3]\\s*\\.\\s*[1-9])|([0-3]\\s*m\\s*[1-9]\\s*s))\\s*.*"))
			.collect(Collectors.toList());

		for (var filter : validSubFilters) {
			var subCount = Integer.parseInt(filter.split("[.m]")[1].replaceAll("[^1-9]", ""));
			var subAbilityName = mapToFullAbilityName(filter.replaceAll("([0-3]\\s*\\.\\s*[1-9])|([0-3]\\s*m\\s*[1-9]\\s*s)", "").trim());

			subFilters.putIfAbsent(subAbilityName, 0);
			subFilters.put(subAbilityName, subFilters.get(subAbilityName) + subCount);
		}

		var prefilteredGear = gearChecker.getAllOwnedGear().stream().filter(g -> mains.contains(g.getPrimaryGearPower().getName())).collect(Collectors.toList());

		var allHeads = prefilteredGear.stream().filter(g -> "HeadGear".equals(g.get__typename())).collect(Collectors.toList());
		if (allHeads.isEmpty()) {
			allHeads = gearChecker.getAllOwnedGear().stream().filter(g -> "HeadGear".equals(g.get__typename())).collect(Collectors.toList());
		}
		allHeads = allHeads.stream()
			.sorted((a, b) -> Integer.compare(getPoints(b, mainFilters, subFilters, preferGrind), getPoints(a, mainFilters, subFilters, preferGrind)))
			.collect(Collectors.toList());

		var allClothes = prefilteredGear.stream().filter(g -> "ClothingGear".equals(g.get__typename())).collect(Collectors.toList());
		if (allClothes.isEmpty()) {
			allClothes = gearChecker.getAllOwnedGear().stream().filter(g -> "ClothingGear".equals(g.get__typename())).collect(Collectors.toList());
		}
		allClothes = allClothes.stream()
			.sorted((a, b) -> Integer.compare(getPoints(b, mainFilters, subFilters, preferGrind), getPoints(a, mainFilters, subFilters, preferGrind)))
			.collect(Collectors.toList());

		var allShoes = prefilteredGear.stream().filter(g -> "ShoesGear".equals(g.get__typename())).collect(Collectors.toList());

		if (allShoes.isEmpty()) {
			allShoes = gearChecker.getAllOwnedGear().stream().filter(g -> "ShoesGear".equals(g.get__typename())).collect(Collectors.toList());
		}
		allShoes = allShoes.stream()
			.sorted((a, b) -> Integer.compare(getPoints(b, mainFilters, subFilters, preferGrind), getPoints(a, mainFilters, subFilters, preferGrind)))
			.collect(Collectors.toList());

		var bestScore = -1;
		var bestHead = allHeads.stream().findFirst().orElseThrow();
		var bestShirt = allClothes.stream().findFirst().orElseThrow();
		var bestShoes = allShoes.stream().findFirst().orElseThrow();

		var breakFors = false;
		for (var head : allHeads) {
			for (var shirt : allClothes) {
				for (var shoes : allShoes) {
					var currentScore = 0;

					final var currentMainFilters = new HashMap<>(mainFilters);
					final var currentSubFilters = new HashMap<>(subFilters);

					// points: gear exclusive = 150, main = 100, sub = 30, ? = 1
					var headName = head.getPrimaryGearPower().getName();
					var shirtName = shirt.getPrimaryGearPower().getName();
					var shoesName = shoes.getPrimaryGearPower().getName();

					currentScore = evaluateScoreByMain(currentScore, currentMainFilters, headName, headAbilities);
					var allMainsFound = currentScore >= 100 || currentMainFilters.keySet().stream().noneMatch(k -> headAbilities.containsValue(k) || genericAbilities.containsValue(k));
					var savedScore = currentScore;

					currentScore = evaluateScoreByMain(currentScore, currentMainFilters, shirtName, clothesAbilities);
					allMainsFound &= currentScore - savedScore >= 100 || currentMainFilters.keySet().stream().noneMatch(k -> clothesAbilities.containsValue(k) || genericAbilities.containsValue(k));
					savedScore = currentScore;

					currentScore = evaluateScoreByMain(currentScore, currentMainFilters, shoesName, shoesAbilities);
					allMainsFound &= currentScore - savedScore >= 100 || currentMainFilters.keySet().stream().noneMatch(k -> shoesAbilities.containsValue(k) || genericAbilities.containsValue(k));

					var allSubs = Stream.of(head.getAdditionalGearPowers(), shirt.getAdditionalGearPowers(), shoes.getAdditionalGearPowers())
						.flatMap(Collection::stream)
						.map(GearPower::getName)
						.collect(Collectors.toList());

					var numberOfSubsFound = 0;
					for (var subAbility : allSubs) {
						if (currentSubFilters.containsKey(subAbility)) {
							currentScore += subPoints;
							numberOfSubsFound++;

							currentSubFilters.put(subAbility, currentSubFilters.get(subAbility) - 1);
							if (currentSubFilters.get(subAbility) < 1) {
								currentSubFilters.remove(subAbility);
							}
						} else if (preferGrind && !genericAbilities.containsValue(subAbility)) {
							// ? ability && prefer grindable gear
							currentScore += otherPoints;
						} else if (!preferGrind && genericAbilities.containsValue(subAbility)) {
							// not ? ability and prefer full gear
							currentScore += otherPoints;
						}
					}

					if (currentScore > bestScore) {
						bestHead = head;
						bestShirt = shirt;
						bestShoes = shoes;
						bestScore = currentScore;
					}

					var numberOfRequestedSubAbilities = subFilters.values().stream().reduce(Integer::sum).orElse(0);
					var numberOfOtherSubAbilities = bestScore % 10;
					if (allMainsFound
						&& (numberOfSubsFound == 9 || (currentSubFilters.isEmpty() && numberOfRequestedSubAbilities + numberOfOtherSubAbilities == 9))) {
						// found a perfect / the best possible match
						breakFors = true;
						break;
					}
				}

				if (breakFors) {
					break;
				}
			}

			if (breakFors) {
				break;
			}
		}

		// result
		if (!all) {
			args.getReplySender().send("**%s** (%s, %s) --- **%s** (%s, %s) --- **%s** (%s, %s)",
				bestHead.getName(), bestHead.getBrand().getName(), bestHead.getPrimaryGearPower().getName(),
				bestShirt.getName(), bestShirt.getBrand().getName(), bestShirt.getPrimaryGearPower().getName(),
				bestShoes.getName(), bestShoes.getBrand().getName(), bestShoes.getPrimaryGearPower().getName());
		} else {
			args.getReplySender().send(String.format("- %s\n - %s\n - %s", describeGear(bestHead), describeGear(bestShirt), describeGear(bestShoes)));
		}
	}

	private String describeGear(Gear gear) {
		var builder = new StringBuilder("**")
			.append(gear.getName())
			.append("** (brand = ")
			.append(gear.getBrand().getName())
			.append(", main = ")
			.append(gear.getPrimaryGearPower().getName())
			.append(", subs =");

		var isFirst = true;
		for (var sub : gear.getAdditionalGearPowers()) {
			if (!isFirst) {
				builder.append(", ");
			}

			builder.append(" ")
				.append(sub.getName());

			isFirst = false;
		}

		return builder.append(")").toString();
	}

	private int getPoints(Gear gear, HashMap<String, Integer> mainFilters, HashMap<String, Integer> subFilters, boolean preferGrind) {
		var points = 0;
		if (mainFilters.containsKey(gear.getPrimaryGearPower().getName())) {
			if (genericAbilities.containsValue(gear.getPrimaryGearPower().getName())) {
				points += mainPoints;
			} else {
				points += exclusivePoints;
			}
		}

		var currentSubFilters = new HashMap<>(subFilters);
		for (var sub : gear.getAdditionalGearPowers()) {
			if (currentSubFilters.containsKey(sub.getName())) {
				points += subPoints;

				currentSubFilters.put(sub.getName(), currentSubFilters.get(sub.getName()) - 1);
				if (currentSubFilters.get(sub.getName()) < 1) {
					currentSubFilters.remove(sub.getName());
				}
			} else if (preferGrind && !genericAbilities.containsValue(sub.getName())) {
				// ? ability && prefer grindable gear
				points += otherPoints;
			} else if (!preferGrind && genericAbilities.containsValue(sub.getName())) {
				// not ? ability and prefer full gear
				points += otherPoints;
			}
		}

		return points;
	}

	private int evaluateScoreByMain(int currentScore, HashMap<String, Integer> currentMainFilters, String headName, Map<String, String> exclusiveAbilities) {
		if (currentMainFilters.containsKey(headName)) {
			if (exclusiveAbilities.containsValue(headName)) {
				currentScore += exclusivePoints;

				currentMainFilters.remove(headName);
			} else {
				currentScore += mainPoints;

				currentMainFilters.put(headName, currentMainFilters.get(headName) - 1);
				if (currentMainFilters.get(headName) < 1) {
					currentMainFilters.remove(headName);
				}
			}
		}
		return currentScore;
	}

	private String mapToFullAbilityName(String shortName) {
		if (headAbilities.containsKey(shortName)) {
			return headAbilities.get(shortName);
		} else if (clothesAbilities.containsKey(shortName)) {
			return clothesAbilities.get(shortName);
		} else if (shoesAbilities.containsKey(shortName)) {
			return shoesAbilities.get(shortName);
		} else {
			return genericAbilities.getOrDefault(shortName, null);
		}
	}

	private String createFilterRegex() {
		return createFilterRegex(true);
	}

	private String createFilterRegex(boolean addStartEndFilter) {
		return Stream.concat(
				Stream.of(
						headAbilities.keySet(),
						clothesAbilities.keySet(),
						shoesAbilities.keySet(),
						genericAbilities.keySet())
					.flatMap(Collection::stream),
				genericAbilities.keySet().stream().map(ga -> String.format("(([0-3]\\s*\\.\\s*[0-9])|([0-3]\\s*m\\s*[0-9]\\s*s))\\s*%s", ga)))
			.map(f -> addStartEndFilter ? String.format("(^%s$)", f) : String.format("(%s)", f))
			.reduce((a, b) -> String.format("%s|%s", a, b)).orElseThrow();
	}
}

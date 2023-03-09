package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3NewGearChecker;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3WeaponDownloader;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class S3RandomWeaponAction extends ChatAction {
	private final Random random = new Random();
	private final S3WeaponDownloader weaponDownloader;
	private final S3NewGearChecker newGearChecker;

	private Instant lastUsed = Instant.now().truncatedTo(ChronoUnit.DAYS);

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.DiscordMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!rw")) {
			if (lastUsed.isBefore(Instant.now().minus(30, ChronoUnit.MINUTES))) {
				weaponDownloader.loadWeapons();
				newGearChecker.checkForNewGearInSplatNetShop(true);

				lastUsed = Instant.now();
			}

			var kits = weaponDownloader.getWeapons();

			for (var weapon : newGearChecker.getAllOwnedWeapons()) {
				kits.stream()
						.filter(k -> Objects.equals(k.getWeaponId(), weapon.getWeaponId()))
						.forEach(k -> k.getWeaponCategory().setName(weapon.getWeaponCategory().getName()));
			}

			var allWeaponClasses = kits.stream()
					.map(k -> k.getWeaponCategory().getName())
					.distinct()
					.collect(Collectors.toList());

			var allSubWeapons = kits.stream()
					.map(k -> k.getSubWeapon().getName())
					.distinct()
					.collect(Collectors.toList());

			var allSpecialWeapons = kits.stream()
					.map(k -> k.getSpecialWeapon().getName())
					.distinct()
					.collect(Collectors.toList());

			// var kits = new ArrayList<>(WeaponKit.All);
			boolean filteredByPointsOrLevel = false;

			if (message.contains("all badges")) {
				filteredByPointsOrLevel = true;
				kits = kits.stream()
						.filter(k -> k.getStats() == null || k.getStats().getLevel() < 4)
						.collect(Collectors.toList());
			}

			if (message.contains("not owned")) {
				kits = kits.stream()
						.filter(k -> k.getStats() == null)
						.collect(Collectors.toList());
			}

			// exact point filters
			String[] foundTurfFilters = extractTurfFilterGroups(message);
			for (String matchedRegex : foundTurfFilters) {
				filteredByPointsOrLevel = true;
				String foundFilter = matchedRegex.trim()
						.replace("k", "000")
						.replace("K", "000")
						.replace("m", "000000")
						.replace("M", "000000")
						.replace("_", "")
						.replace(" ", "");

				long number = Integer.parseInt(foundFilter.replaceAll("\\D*", ""));
				String prefix = foundFilter.replaceAll("[^<>=]*", "");

				switch (prefix) {
					case "=":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getPaint() : 0) == number).collect(Collectors.toList());
						break;
					case ">":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getPaint() : 0) > number).collect(Collectors.toList());
						break;
					case ">=":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getPaint() : 0) >= number).collect(Collectors.toList());
						break;
					case "<":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getPaint() : 0) < number).collect(Collectors.toList());
						break;
					case "<=":
					default:
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getPaint() : 0) <= number).collect(Collectors.toList());
						break;
				}
			}

			// exact point filters
			String[] foundStarFilters = extractStarsFilterGroups(message);
			for (String matchedRegex : foundStarFilters) {
				filteredByPointsOrLevel = true;
				String foundFilter = matchedRegex.trim()
						.replace(" ", "");

				long number = Integer.parseInt(foundFilter.replaceAll("[^0-5]*", ""));
				String prefix = foundFilter.replaceAll("[^<>=]*", "");

				switch (prefix) {
					case "=":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getLevel() : 0) == number).collect(Collectors.toList());
						break;
					case ">":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getLevel() : 0) > number).collect(Collectors.toList());
						break;
					case ">=":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getLevel() : 0) >= number).collect(Collectors.toList());
						break;
					case "<":
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getLevel() : 0) < number).collect(Collectors.toList());
						break;
					case "<=":
					default:
						kits = kits.stream().filter(k -> (k.getStats() != null ? k.getStats().getLevel() : 0) <= number).collect(Collectors.toList());
						break;
				}
			}

			List<String> chosenClasses = new ArrayList<>();
			for (String weaponClass : allWeaponClasses) {
				if (message.contains(weaponClass.toLowerCase(Locale.ROOT))) {
					chosenClasses.add(weaponClass);
				}
			}

			if (chosenClasses.size() > 0) {
				kits = kits.stream().filter(k -> chosenClasses.contains(k.getWeaponCategory().getName())).collect(Collectors.toList());
			}

			List<String> chosenSubs = new ArrayList<>();
			for (String subWeapon : allSubWeapons) {
				if (message.contains(subWeapon.toLowerCase(Locale.ROOT))) {
					chosenSubs.add(subWeapon);
				}
			}

			if (chosenSubs.size() > 0) {
				kits = kits.stream().filter(k -> chosenSubs.contains(k.getSubWeapon().getName())).collect(Collectors.toList());
			}

			List<String> chosenSpecials = new ArrayList<>();
			for (String specialWeapon : allSpecialWeapons) {
				if (message.contains(specialWeapon.toLowerCase(Locale.ROOT))) {
					chosenSpecials.add(specialWeapon);
				}
			}

			if (chosenSpecials.size() > 0) {
				kits = kits.stream().filter(k -> chosenSpecials.contains(k.getSpecialWeapon().getName())).collect(Collectors.toList());
			}

			if (kits.size() > 0) {
				var chosenWeapon = kits.get(random.nextInt(kits.size()));

				String weaponPoints = "";
				if (filteredByPointsOrLevel) {
					DecimalFormat df = new DecimalFormat("#,###");
					weaponPoints = String.format(" -> %s points",
							df.format(chosenWeapon.getStats() != null ? chosenWeapon.getStats().getPaint() : 0)
									.replace(',', ' ')
									.replace('.', ' '));
				}

				String weaponLevel = "";
				if (filteredByPointsOrLevel) {
					DecimalFormat df = new DecimalFormat("#,###");
					weaponLevel = String.format(", %s stars",
							df.format(chosenWeapon.getStats() != null ? chosenWeapon.getStats().getLevel() : 0)
									.replace(',', ' ')
									.replace('.', ' '));
				}

				String replyMessage = String.format("%s (%s, %s)%s%s", chosenWeapon.getName(), chosenWeapon.getSubWeapon().getName(), chosenWeapon.getSpecialWeapon().getName(), weaponPoints, weaponLevel);
				args.getReplySender().send(replyMessage);
			} else {
				args.getReplySender().send("No weapon kit matches your criteria. Please note that combining 'not owned' with a weapon class does not work.");
			}
		}
	}

	private String[] extractTurfFilterGroups(String message) {
		return Pattern.compile("((<|<=|>|>=|=) *[0-9_]+([kKmM])? *p(aint)?)")
				.matcher(message)
				.results()
				.map(mr -> mr.group(1))
				.toArray(String[]::new);
	}

	private String[] extractStarsFilterGroups(String message) {
		return Pattern.compile("((<|<=|>|>=|=) *[0-5] *s(tars)?)")
				.matcher(message)
				.results()
				.map(mr -> mr.group(1))
				.toArray(String[]::new);
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.BuildFilter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.BuildFilterRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class S3FindBestGearPresetsAction extends ChatAction {
	private final S3FindBestGearAction findBestGearAction;
	private final BuildFilterRepository buildFilterRepository;

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage, TriggerReason.DiscordMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (!message.startsWith("!gear")) {
			return;
		}

		message = message.substring("!gear".length()).trim();

		if (message.startsWith("add ")) {
			message = message.substring("add".length()).trim();

			var parts = message.split("=");
			if (parts.length != 2) {
				args.getReplySender().send(String.format("ERROR: wrong number of equals, expected one, got %d", parts.length - 1));
				return;
			}

			if (parts[0].contains("<<<") || parts[0].contains(">>>")) {
				args.getReplySender().send("ERROR: name must not contain <<< and must not contain >>>");
				return;
			}

			var allNames = extractAllNames(parts[0], args);

			if (allNames.isEmpty()) {
				return;
			}

			var allExistingBuildFilters = buildFilterRepository.findAllByNameIn(allNames);

			var saved = buildFilterRepository.saveAll(
				allNames.stream()
					.map(name -> allExistingBuildFilters.stream()
						.filter(n -> name.trim().equals(n.getName().trim()))
						.findFirst()
						.map(bf -> bf.toBuilder().parameters(parts[1].trim()).build())
						.orElse(new BuildFilter(null, name.trim(), parts[1].trim())))
					.collect(Collectors.toList()));

			var savedList = StreamSupport.stream(saved.spliterator(), false).collect(Collectors.toList());

			args.getReplySender().send(String.format("Saved %d filters.", savedList.size()));
		} else if (message.startsWith("remove ")) {
			message = message.substring("remove".length()).trim();

			if (message.contains("<<<") || message.contains(">>>")) {
				args.getReplySender().send("ERROR: name must not contain <<< and must not contain >>>");
				return;
			}

			var allNames = extractAllNames(message, args);

			if (allNames.isEmpty()) {
				return;
			}

			var allExistingBuildFilters = buildFilterRepository.findAllByNameIn(allNames);
			buildFilterRepository.deleteAll(allExistingBuildFilters);

			args.getReplySender().send(String.format("Removed %d filters.", allExistingBuildFilters.size()));
		} else { // get gear
			var filterName = message.trim();

			var filterAll = false;
			if (filterName.startsWith("all ")) {
				filterAll = true;
				filterName = filterName.substring("all ".length()).trim();
			}
			var filterGrind = false;
			if (filterName.startsWith("grind ")) {
				filterGrind = true;
				filterName = filterName.substring("grind ".length()).trim();
			}

			var bfOptional = buildFilterRepository.findByName(filterName);

			if (bfOptional.isPresent()) {
				var bf = bfOptional.get();

				args.getArguments().put(ArgumentKey.Message, String.format("%s %s %s %s",
					S3FindBestGearAction.COMMAND_NAME,
					filterAll ? "all" : "",
					filterGrind ? "grind" : "",
					bf.getParameters()));

				findBestGearAction.execute(args);
			} else {
				args.getReplySender().send(String.format("A filter with the name '%s' does not exist.", filterName));
			}
		}
	}

	private List<String> extractAllNames(String namePart, ActionArgs args) {
		var replacements = new HashMap<String, List<String>>();
		var currentLayer = 0;
		var maxLayer = 0;
		do {
			maxLayer = 0;
			currentLayer = 0;

			for (int i = 0; i < namePart.length(); i++) {
				var c = namePart.charAt(i);
				if (c == '(') {
					currentLayer++;
					maxLayer = Math.max(currentLayer, maxLayer);
				} else if (c == ')') {
					currentLayer--;
				}
			}

			if (currentLayer != 0) {
				args.getReplySender().send("ERROR: mismatch between opened and closed brackets ()!");
				return List.of();
			}

			if (maxLayer > 0) {
				var alternations = new ArrayList<StringBuilder>();
				for (int i = 0; i < namePart.length(); i++) {
					var c = namePart.charAt(i);
					if (c == '(') {
						currentLayer++;
						if (currentLayer == maxLayer) {
							alternations.add(new StringBuilder());
						}
					} else if (c == ')') {
						if (currentLayer == maxLayer) {
							// done => add to replacements table!
							var completeString = String.format("(%s)", alternations.stream()
								.map(StringBuilder::toString)
								.reduce((a, b) -> String.format("%s|%s", a, b))
								.orElse(""));

							var replacementName = String.format("<<<%d>>>", replacements.size());
							namePart = namePart.replace(completeString, replacementName);
							replacements.put(replacementName, alternations.stream().map(StringBuilder::toString).collect(Collectors.toList()));

							break;
						}

						currentLayer--;
					} else if (c == '|') {
						// ... manage alternations
						if (currentLayer == maxLayer) {
							alternations.add(new StringBuilder());
						}
					} else if (currentLayer == maxLayer) {
						alternations.get(alternations.size() - 1).append(c);
					}
				}
			}
		} while (maxLayer > 0);

		var allNames = new ArrayList<String>();
		allNames.add(namePart);

		var sortedReplacements = replacements.entrySet().stream()
			.sorted((a, b) -> Integer.compare(
				Integer.parseInt(b.getKey().replace("<<<", "").replace(">>>", "")),
				Integer.parseInt(a.getKey().replace("<<<", "").replace(">>>", ""))))
			.collect(Collectors.toList());

		for (var entry : sortedReplacements) {
			var namesSave = new ArrayList<String>();

			for (var name : allNames) {
				for (var replacement : entry.getValue()) {
					namesSave.add(name.replace(entry.getKey(), replacement));
				}
			}

			allNames.clear();
			allNames.addAll(namesSave.stream().map(String::trim).distinct().collect(Collectors.toList()));
		}

		return allNames;
	}
}

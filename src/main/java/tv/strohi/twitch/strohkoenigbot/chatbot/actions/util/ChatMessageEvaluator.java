package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import tv.strohi.twitch.strohkoenigbot.utils.RegexUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatMessageEvaluator {
	private final List<String> flags = new ArrayList<>();
	private final List<Class<? extends Enum>> enums = new ArrayList<>();
	private final List<String> regexes = new ArrayList<>();

	public Map<Object, Object> evaluate(String text) {
		Map<Object, Object> results = new HashMap<>();

		for (String flag : flags) {
			Matcher matcher = Pattern.compile(String.format("(^|\\s)%s($|\\s)", RegexUtils.escapeQuotes(flag))).matcher(text);
			results.put(flag, matcher.find());
		}

		String[] splits = text.split("\\s");
		for (Class<? extends Enum> singleClass : enums) {
			ArrayList<Enum<?>> list = new ArrayList<>();

			for (String word : splits) {
				try {
					list.add(Enum.valueOf(singleClass, word));
				} catch (Exception ignored) {
				}
			}

			results.put(singleClass, list);
		}

		for (String regex : regexes) {
			ArrayList<String> list = new ArrayList<>();

			try {
				Matcher matcher = Pattern.compile(regex).matcher(text);

				while (matcher.find()) {
					list.add(matcher.group());
				}
			} catch (Exception ignored) {
			}

			results.put(regex, list);
		}

		String removedText = text;

		for (var result : results.entrySet().stream().filter(r -> r.getValue() != null).collect(Collectors.toList())) {
			if (result.getValue() instanceof Boolean) {
				if ((Boolean) result.getValue()) {
					Matcher matcher = Pattern.compile(String.format("(^|\\s)%s($|\\s)", RegexUtils.escapeQuotes((String) result.getKey()))).matcher(removedText);
					removedText = matcher.replaceAll("");
				}
			} else {
				List foundResults = (ArrayList) result.getValue();
				for (Object foundResult : foundResults) {
					removedText = removedText.replace(foundResult.toString(), "");
				}
			}
		}

		results.put(ChatMessageEvaluator.class, Arrays.stream(removedText.split("\\s")).filter(rs -> !rs.isBlank()).collect(Collectors.toList()));

		return results;
	}

	private void registerFlag(String flagName) {
		if (flagName != null && !flagName.isBlank() && !flags.contains(flagName) && !regexes.contains(flagName)) {
			flags.add(flagName);
		}
	}

	private void registerEnum(Class<? extends Enum<?>> enumClass) {
		if (enumClass != null) {
			enums.add(enumClass);
		}
	}

	private void registerRegex(String regex) {
		if (regex != null && !regex.isBlank() && !flags.contains(regex) && !regexes.contains(regex)) {
			regexes.add(regex);
		}
	}

	public static ChatMessageEvaluatorBuilder builder() {
		return new ChatMessageEvaluatorBuilder();
	}

	public static class ChatMessageEvaluatorBuilder {
		private final ChatMessageEvaluator chatMessageEvaluator = new ChatMessageEvaluator();

		private ChatMessageEvaluatorBuilder() {
		}

		public ChatMessageEvaluatorBuilder withFlag(String flag) {
			chatMessageEvaluator.registerFlag(flag);
			return this;
		}

		public ChatMessageEvaluatorBuilder withEnum(Class<? extends Enum<?>> enumClass) {
			chatMessageEvaluator.registerEnum(enumClass);
			return this;
		}

		public ChatMessageEvaluatorBuilder withRegex(String regex) {
			chatMessageEvaluator.registerRegex(regex);
			return this;
		}

		public ChatMessageEvaluator build() {
			return chatMessageEvaluator;
		}
	}
}

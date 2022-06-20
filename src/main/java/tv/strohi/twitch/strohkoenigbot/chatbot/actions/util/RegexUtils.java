package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.DayFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.DayFilterWithTimeString;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RegexUtils {
	private TextFilters textFilters;

	@Autowired
	public void setTextFilters(TextFilters textFilters) {
		this.textFilters = textFilters;
	}

	public <T> String fillListAndReplaceText(String text, Map<T, Pattern> map, List<T> listToFill) {
		for (Map.Entry<T, Pattern> enumAndPattern : map.entrySet()) {
			String[] results = enumAndPattern.getValue()
					.matcher(text)
					.results()
					.map(mr -> mr.group(0))
					.toArray(String[]::new);

			if (results.length > 0) {
				if (!listToFill.contains(enumAndPattern.getKey())) {
					listToFill.add(enumAndPattern.getKey());
				}

				for (String result : results) {
					text = text.replace(result, "xxx");
				}
			}
		}

		return text;
	}

	public String fillDayFilterWithTimeList(String text, Map<DayFilter, Pattern> map, List<DayFilterWithTimeString> listToFill) {
		for (Map.Entry<DayFilter, Pattern> dayAndPattern : map.entrySet()) {
			String[] results = dayAndPattern.getValue()
					.matcher(text)
					.results()
					.map(mr -> mr.group(0))
					.toArray(String[]::new);

			for (String result : results) {
				DayFilter filter = dayAndPattern.getKey();
				String time = textFilters.getTimeFilter()
						.matcher(result)
						.results()
						.map(mr -> mr.group(0))
						.findFirst()
						.orElse("0-23")
						.replaceAll("\\s", "");

				if (listToFill.stream().noneMatch(dayAndTime -> dayAndTime.getFilter() == filter)) {
					listToFill.add(new DayFilterWithTimeString(filter, time, Integer.parseInt(time.split("-")[0]), Integer.parseInt(time.split("-")[1])));
				}

				text = text.replace(result, "xxx");
			}
		}

		return text;
	}
}

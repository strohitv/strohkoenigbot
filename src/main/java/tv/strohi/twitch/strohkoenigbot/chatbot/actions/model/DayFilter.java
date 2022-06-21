package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum DayFilter {
	Monday(1, "Monday", "mo", "mon"),
	Tuesday(2, "Tuesday", "tu", "tue"),
	Wednesday(4, "Wednesday", "we", "wed"),
	Thursday(8, "Thursday", "th", "thu"),
	Friday(16, "Friday", "fr", "fri"),
	Saturday(32, "Saturday", "sa", "sat"),
	Sunday(64, "Sunday", "su", "sun")	;

	private final int flag;
	private final String name;
	private final String[] altNames;

	DayFilter(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<DayFilter> All = EnumSet.allOf(DayFilter.class);

	public static DayFilter[] resolveFromNumber(int number) {
		return All.stream().filter(gs -> (gs.flag & number) == gs.flag).toArray(DayFilter[]::new);
	}

	public static int resolveToNumber(List<DayFilter> gearSlots) {
		return gearSlots.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}
}

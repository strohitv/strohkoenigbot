package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;

@Getter
public enum DayFilter {
	Monday(1, DayOfWeek.MONDAY, "Monday", "mo", "mon"),
	Tuesday(2, DayOfWeek.TUESDAY, "Tuesday", "tu", "tue"),
	Wednesday(4, DayOfWeek.WEDNESDAY, "Wednesday", "we", "wed"),
	Thursday(8, DayOfWeek.THURSDAY, "Thursday", "th", "thu"),
	Friday(16, DayOfWeek.FRIDAY, "Friday", "fr", "fri"),
	Saturday(32, DayOfWeek.SATURDAY, "Saturday", "sa", "sat"),
	Sunday(64, DayOfWeek.SUNDAY, "Sunday", "su", "sun")	;

	private final int flag;
	private final DayOfWeek day;
	private final String name;
	private final String[] altNames;

	DayFilter(int flag, DayOfWeek day, String name, String... altNames) {
		this.flag = flag;
		this.day = day;
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

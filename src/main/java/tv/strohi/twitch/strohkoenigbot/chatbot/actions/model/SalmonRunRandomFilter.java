package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum SalmonRunRandomFilter {
	TurfWar(1, 0, "None", "0", "Zero"),
	Ranked(2, 1, "One", "1", "Some"),
	League(4, 4, "All", "4", "Four");

	private final int flag;
	private final int randomCount;
	private final String name;
	private final String[] altNames;

	SalmonRunRandomFilter(int flag, int randomCount, String name, String... altNames) {
		this.flag = flag;
		this.randomCount = randomCount;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<SalmonRunRandomFilter> All = EnumSet.allOf(SalmonRunRandomFilter.class);

	public static SalmonRunRandomFilter[] resolveFromNumber(int number) {
		return All.stream().filter(gs -> (gs.flag & number) == gs.flag).toArray(SalmonRunRandomFilter[]::new);
	}

	public static int resolveToNumber(List<SalmonRunRandomFilter> gearSlots) {
		return gearSlots.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}
}

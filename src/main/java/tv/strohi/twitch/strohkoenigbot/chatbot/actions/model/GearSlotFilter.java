package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum GearSlotFilter {
	One(1, 1, "1", "One"),
	Two(2, 2, "2", "Two"),
	Three(4, 3, "3", "Three");

	private final int flag;
	private final int slots;
	private final String name;
	private final String[] altNames;

	GearSlotFilter(int flag, int slots, String name, String... altNames) {
		this.flag = flag;
		this.slots = slots;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<GearSlotFilter> All = EnumSet.allOf(GearSlotFilter.class);

	public static GearSlotFilter[] resolveFromNumber(int number) {
		return All.stream().filter(gs -> (gs.flag & number) == gs.flag).toArray(GearSlotFilter[]::new);
	}

	public static int resolveToNumber(List<GearSlotFilter> gearSlots) {
		return gearSlots.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}
}

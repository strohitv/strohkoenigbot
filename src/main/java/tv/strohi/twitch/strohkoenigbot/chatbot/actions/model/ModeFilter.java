package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum ModeFilter {
	TurfWar(1, "Turf War", "Turfwar", "Turf", "War", "tw"),
	Ranked(2, "Ranked", "SoloQ", "Solo"),
	League(4, "League", "Twin", "Quad", "Team");

	private final int flag;
	private final String name;
	private final String[] altNames;

	ModeFilter(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<ModeFilter> All = EnumSet.allOf(ModeFilter.class);

	public static ModeFilter getFromSplatNetApiName(String name) {
		ModeFilter mode = Ranked;

		if ("League Battle".equals(name)) {
			mode = League;
		} else if ("Regular Battle".equals(name)) {
			mode = TurfWar;
		}

		return mode;
	}
}

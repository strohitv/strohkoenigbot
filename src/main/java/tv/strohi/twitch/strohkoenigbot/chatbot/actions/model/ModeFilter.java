package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum ModeFilter {
	TurfWar(1, "Turf War", "Turfwar", "Turf", "War", "tw"),
	Ranked(2, "Ranked", "SoloQ", "Solo"),
	League(4, "League", "Twin", "Quad", "Team"),
	RegularBattle(8, "Regular Battle", "Regular", "rb"),
	AnarchyOpen(16, "Anarchy Open", "Offen", "Anarchy Open"),
	AnarchySeries(32, "Anarchy Series", "Serie", "Anarchy Series"),
	SplatfestOpen(64, "Splatfest Open", "Splatfest", "Fest Open"),
	SplatfestPro(128, "Splatfest Pro", "Profest", "Fest Pro", "Pro"),
	SplatfestTricolor(256, "Splatfest Tricolor", "Trifest", "Tricolor", "Tri"),
	XBattle(512, "X Battle", "X Rank", "X-Rang", "X"),
	Challenge(1024, "Challenge");

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
		var mode = Ranked;

		if ("League Battle".equals(name)) {
			mode = League;
		} else if ("Regular Battle".equals(name)) {
			mode = TurfWar;
		} else if ("Anarchy Series".equals(name)) {
			mode = AnarchySeries;
		} else if ("Anarchy Open".equals(name)) {
			mode = AnarchyOpen;
		} else if ("X Battle".equals(name)) {
			mode = XBattle;
		} else if ("Challenge".equals(name)) {
			mode = Challenge;
		} else if ("Splatfest Open".equals(name)) {
			mode = SplatfestOpen;
		} else if ("Splatfest Pro".equals(name)) {
			mode = SplatfestPro;
		} else if ("Splatfest Tricolor".equals(name)) {
			mode = SplatfestTricolor;
		}

		return mode;
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum RuleFilter {
	TurfWar("Turf War", "Turfwar", "Turf", "War", "tw"),
	SplatZones("Splat Zones", "Splatzones", "Splat", "Zones", "sz"),
	Rainmaker("Rainmaker", "Rain", "Maker", "rm"),
	TowerControl("Tower Control", "Towercontrol", "Tower", "Control", "tc"),
	ClamBlitz("Clam Blitz", "Clamblitz", "Clams", "Clam", "Blitz", "cb");

	private final String name;
	private final String[] altNames;

	RuleFilter(String name, String... altNames) {
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<RuleFilter> All = EnumSet.allOf(RuleFilter.class);
	public static final EnumSet<RuleFilter> RankedModes = EnumSet.of(SplatZones, Rainmaker, TowerControl, ClamBlitz);

	public static RuleFilter getFromSplatNetApiName(String name) {
		RuleFilter rule = SplatZones;

		if ("Rainmaker".equals(name)) {
			rule = Rainmaker;
		} else if ("Tower Control".equals(name)) {
			rule = TowerControl;
		} else if ("Clam Blitz".equals(name)) {
			rule = ClamBlitz;
		} else if ("Turf War".equals(name)) {
			rule = TurfWar;
		}

		return rule;
	}
}

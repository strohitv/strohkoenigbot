package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum SplatoonSalmonRunStage {
	// TODO idea for salmon run filter:
	//  - stages
	//  - no random weapons / some random weapons / all random weapons
	//  - ??? I should ask people who regularly play salmon run LMAO

	SpawningGrounds(1, "Spawning Grounds", "Spawning", "Grounds", "sg"),
	MaroonersBay(2, "Marooner's Bay", "Marooners Bay", "Marooner's", "Marooners", "Bay", "mb"),
	LostOutpost(4, "Lost Outpost", "Lost", "Outpost", "lo"),
	SalmonidSmokeyard(8, "Salmonid Smokeyard", "Salmonid", "Smokeyard", "ss"),
	RuinsOfArkPolaris(16, "Ruins of Ark Polaris", "Ruins", "Ark Polaris", "Ark", "Polaris", "roap", "rap", "ra", "ap");

	private final int flag;
	private final String name;
	private final String[] altNames;

	SplatoonSalmonRunStage(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<SplatoonSalmonRunStage> All = EnumSet.allOf(SplatoonSalmonRunStage.class);
}

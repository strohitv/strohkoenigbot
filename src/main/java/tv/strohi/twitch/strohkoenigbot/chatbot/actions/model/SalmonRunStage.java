package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum SalmonRunStage {
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

	SalmonRunStage(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<SalmonRunStage> All = EnumSet.allOf(SalmonRunStage.class);

	public static SalmonRunStage[] resolveFromNumber(int number) {
		return All.stream().filter(s -> (s.flag & number) == s.flag).toArray(SalmonRunStage[]::new);
	}

	public static int resolveToNumber(List<SalmonRunStage> stages) {
		return stages.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}

	public static SalmonRunStage getFromSplatNetApiName(String name) {
		return All.stream().filter(s -> s.getName().equals(name)).findFirst().orElse(SpawningGrounds);
	}
}

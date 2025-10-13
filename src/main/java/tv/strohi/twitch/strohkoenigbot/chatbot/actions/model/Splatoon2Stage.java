package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum Splatoon2Stage {
	Reef(1, "The Reef", "Reef"),
	Musselforge(2, "Musselforge Fitness", "Musselforge", "Fitness"),
	Starfish(4, "Starfish Mainstage", "Starfish", "Mainstage"),
	Humpback(8, "Humpback Pump Track", "Humpback Pumptrack", "Pumptrack", "Humpback Pump", "Pump Track", "Humpback Track", "Humpback", "Pump", "Track", "Perfection"),
	Inkblot(16, "Inkblot Art Academy", "Inkblot Art", "Art Academy", "Inkblot Academy", "Inkblot", "Art", "Academy"),
	Sturgeon(32, "Sturgeon Shipyard", "Sturgeon", "Shipyard"),
	Moray(64, "Moray Towers", "Moray", "Towers", "Shit"),
	Port(128, "Port Mackerel", "Port", "Mackerel"),
	Manta(256, "Manta Maria", "Manta", "Maria"),
	Kelp(512, "Kelp Dome", "Kelp", "Dome", "Garbage"),
	Snapper(1024, "Snapper Canal", "Snapper", "Canal"),
	Blackbelly(2048, "Blackbelly Skatepark", "Blackbelly", "Skatepark"),
	Mako(4096, "MakoMart", "Mako Mart", "Mako", "Mart"),
	Walleye(8192, "Walleye Warehouse", "Walleye", "Warehouse", "Trash"),
	Shellendorf(16384, "Shellendorf Institute", "Shellendorf", "Institute"),
	Arowana(32768, "Arowana Mall", "Arowana", "Mall"),
	Goby(65536, "Goby Arena", "Goby", "Arena", "Waste"),
	Piranha(131072, "Piranha Pit", "Piranha", "Pit"),
	Camp(262144, "Camp Triggerfish", "Camp", "Triggerfish", "Diarrhea"),
	Wahoo(524288, "Wahoo World", "Wahoo", "World"),
	Albacore(1048576, "New Albacore Hotel", "Albacore Hotel", "New Hotel", "New Albacore", "New", "Albacore", "Hotel"),
	AnchoV(2097152, "Ancho-V Games", "Ancho-V", "Games", "AnchoV Games", "AnchoV"),
	Skipper(4194304, "Skipper Pavilion", "Skipper", "Pavilion");

	private final int flag;
	private final String name;
	private final String[] altNames;

	Splatoon2Stage(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<Splatoon2Stage> All = EnumSet.allOf(Splatoon2Stage.class);

	public static Splatoon2Stage[] resolveFromNumber(int number) {
		return All.stream().filter(s -> (s.flag & number) == s.flag).toArray(Splatoon2Stage[]::new);
	}

	public static int resolveToNumber(List<Splatoon2Stage> stages) {
		return stages.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}
}

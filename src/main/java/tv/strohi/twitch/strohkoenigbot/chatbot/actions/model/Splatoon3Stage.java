package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

@Getter
public enum Splatoon3Stage {
	Scorge(1, "Scorch Gorge", "Scorch", "Gorge"),
	Eeltail(2, "Eeltail Alley", "Eeltail", "Alley"),
	Hagglefish(4, "Hagglefish Market", "Hagglefish", "Market", "Haggle"),
	Undertow(8, "Undertow Spillway", "Undertow", "Spillway"),
	Mincemeat(16, "Mincemeat Metalworks", "Mincemeat", "Metalworks", "Mince"),
	Hammerhead(32, "Hammerhead Bridge", "Hammerhead", "Bridge", "Hammer"),
	Museum(64, "Museum d'Alfonsino", "Museum dAlfonsino", "Museum", "d'Alfonsino", "Alfonsino"),
	Mahi(128, "Mahi-Mahi Resort", "Mahi Mahi Resort", "Mahi", "Resort", "Mahi-Mahi"),
	Inkblot(256, "Inkblot Art Academy", "Inkblot Art", "Art Academy", "Inkblot Academy", "Inkblot", "Art", "Academy"),
	Sturgeon(512, "Sturgeon Shipyard", "Sturgeon", "Shipyard"),
	Mako(1024, "MakoMart", "Mako Mart", "Mako", "Mart"),
	Wahoo(2048, "Wahoo World", "Wahoo", "World"),
	Brinewater(4096, "Brinewater Springs", "Brinewater", "Springs", "Brine", "Perfection", "Love"),
	Flounder(8192, "Flounder Heights", "Flounder", "Heights", "Trash"),
	Umami(16384, "Um'ami Ruins", "Um'ami", "Ruins", "Umami", "Umami Ruins"),
	Manta(32768, "Manta Maria", "Manta", "Maria"),
	Barnacle(65536, "Barnacle & Dime", "Barnacle Dime", "Barnacle", "Dime"),
	Humpback(131072, "Humpback Pump Track", "Humpback Pumptrack", "Pumptrack", "Humpback Pump", "Pump Track", "Humpback Track", "Humpback", "Pump", "Track", "Perfection"),
	Crableg(262144, "Crableg Capital", "Crableg", "Capital", "Dogshit"),
	Shipshape(524288, "Shipshape Cargo Co.", "Shipshape Cargo Co", "Shipshape", "Cargo", "Co.", "Co"),
	Robo(1048576, "Robo ROM-en", "Robo ROMen", "Robo", "ROM-en", "ROM", "Robo ROM"),
	Bluefin(2097152, "Bluefin Depot", "Bluefin", "Depot", "Diarrhea"),
	Marlin(4194304, "Marlin Airport", "Marlin", "Airport"),
	Lemuria(8388608, "Lemuria Hub", "Lemuria", "Hub", "Garbage"),
	Urchin(16777216, "Urchin Underpass", "Urchin", "Underpass");

	private final int flag;
	private final String name;
	private final String[] altNames;

	Splatoon3Stage(int flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final EnumSet<Splatoon3Stage> All = EnumSet.allOf(Splatoon3Stage.class);

	public static Splatoon3Stage[] resolveFromNumber(int number) {
		return All.stream().filter(s -> (s.flag & number) == s.flag).toArray(Splatoon3Stage[]::new);
	}

	public static int resolveToNumber(List<Splatoon3Stage> stages) {
		return stages.stream().map(s -> s.flag).reduce(0, Integer::sum);
	}
}

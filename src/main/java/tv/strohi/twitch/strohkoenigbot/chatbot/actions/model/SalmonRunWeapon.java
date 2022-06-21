package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

import lombok.Getter;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum SalmonRunWeapon {
	Gal52(1L, ".52 Gal", "52", "kgal", "52 gal", "gal 52"),
	Gal96(2L, ".96 Gal", "96", "96 gal", "gal 96"),
	Aerospray(4L, "Aerospray MG", "Aerospray", "MG", "Aero", "Spray"),
	Ballpoint(8L, "Ballpoint Splatling", "Ballpoint", "BPN", "VBP"),
	Bamboo(16L, "Bamboozler 14 Mk I", "Bamboo", "MK I", "MKI"),
	Undercover(32L, "Undercover Brella", "Undercover", "Under"),
	Bloblobber(64L, "Bloblobber", "Blob"),
	Carbon(128L, "Carbon Roller", "Carbon"),
	Clash(256L, "Clash Blaster", "Clash"),
	Squiffer(512L, "Classic Squiffer", "Squiffer", "Classic"),
	Dapples(1024L, "Dapple Dualies", "Dapple", "Dapples"),
	Tetras(2048L, "Dark Tetra Dualies", "Tetras", "Tetra", "Dark Tetra", "Dark"),
	DualieSquelchers(4096L, "Dualie Squelchers", "CDS", "VDS", "Squelchers"),
	Dynamo(8192L, "Dynamo Roller", "Dynamo", "Dyna"),
	EliterScope(16384L, "E-liter 4K Scope", "4K Scope", "Eliter Scope", "E-Liter Scope"),
	Eliter(32768L, "E-liter 4K", "4K", "Eliter", "E-Liter"),
	Explo(65536L, "Explosher", "Explo"),
	Flingza(131072L, "Flingza Roller", "Flingza"),
	Gloogas(262144L, "Glooga Dualies", "Gloogas", "Glooga"),
	GooTuber(524288L, "Goo Tuber", "Goo", "Tuber"),
	H3(1048576L, "H-3 Nozzlenose", "H3", "H-3"),
	Heavy(2097152L, "Heavy Splatling", "Heavy"),
	Hydra(4194304L, "Hydra Splatling", "Hydra"),
	InkBrush(8388608L, "Inkbrush", "Ink Brush"),
	Jet(16777216L, "Jet Squelcher", "Jet", "cJet", "vJet"),
	L3(33554432L, "L-3 Nozzlenose", "L3", "L-3"),
	Luna(67108864L, "Luna Blaster", "Luna"),
	Mini(134217728L, "Mini Splatling", "Mini"),
	Nzap(268435456L, "N-ZAP '85", "Nzap", "N-ZAP", "Nzap 85", "Nzap '85", "N-Zap 85"),
	Nautilus(536870912L, "Nautilus", "Naut"),
	Octobrush(1073741824L, "Octobrush", "Octo Brush"),
	RangeBlaster(2147483648L, "Range Blaster", "Range"),
	RapidBlaster(4294967296L, "Rapid Blaster", "Rapid"),
	RapidBlasterPro(8589934592L, "Rapid Blaster Pro", "Rapid Pro"),
	Slosher(17179869184L, "Slosher"),
	SloshingMachine(34359738368L, "Sloshing Machine", "Machine", "Sloshing"),
	Splash(68719476736L, "Splash-o-matic", "Splash"),
	Splatterscope(137438953472L, "Splatterscope", "Scope", "Charger Scope"),
	SplatCharger(274877906944L, "Splat Charger", "Charger", "Firefin"),
	SplatDualies(549755813888L, "Splat Dualies", "Dualies"),
	SplatRoller(1099511627776L, "Splat Roller", "Roller"),
	SplatBrella(2199023255552L, "Splat Brella", "Brella"),
	Splattershot(4398046511104L, "Splattershot", "shot", "kshot", "ttek", "vshot"),
	SplattershotJr(8796093022208L, "Splattershot Jr.", "Jr", "Jr.", "Splattershot Jr"),
	SplattershotPro(17592186044416L, "Splattershot Pro", "Pro", "Forge"),
	Sploosh(35184372088832L, "Sploosh-o-matic", "Sploosh"),
	Squeezer(70368744177664L, "Squeezer"),
	TentaBrella(140737488355328L, "Tenta Brella", "Tent", "Tenta"),
	TriSlosher(281474976710656L, "Tri-Slosher", "Tri", "Tri Slosher"),
	Blaster(562949953421312L, "Blaster");

	private final long flag;
	private final String name;
	private final String[] altNames;

	SalmonRunWeapon(long flag, String name, String... altNames) {
		this.flag = flag;
		this.name = name;
		this.altNames = altNames;
	}

	public static final List<SalmonRunWeapon> All = EnumSet.allOf(SalmonRunWeapon.class).stream().sorted(Comparator.comparingLong(a -> a.flag)).collect(Collectors.toList());

	public static SalmonRunWeapon[] resolveFromNumber(long number) {
		return All.stream().filter(s -> (s.flag & number) == s.flag).toArray(SalmonRunWeapon[]::new);
	}

	public static long resolveToNumber(List<SalmonRunWeapon> stages) {
		return stages.stream().map(s -> s.flag).reduce(0L, Long::sum);
	}

	public static SalmonRunWeapon getFromSplatNetApiName(String name) {
		return All.stream().filter(s -> s.getName().equals(name)).findFirst().orElse(SplattershotJr);
	}
}

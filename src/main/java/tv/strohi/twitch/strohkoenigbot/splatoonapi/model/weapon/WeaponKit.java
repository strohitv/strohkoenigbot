package tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon;

import lombok.Getter;

@Getter
public enum WeaponKit {
	// Shooters
	SplooshOMatic("Sploosh-o-matic", WeaponClass.Shooter, SubWeapon.CurlingBomb, SpecialWeapon.Splashdown),
	NeoSplooshOMatic("Neo Sploosh-o-matic", WeaponClass.Shooter, SubWeapon.SquidBeakon, SpecialWeapon.TentaMissiles),
	SplooshOMatic7("Sploosh-o-matic 7", WeaponClass.Shooter, SubWeapon.SplatBomb, SpecialWeapon.UltraStamp),

	SplattershotJr("Splattershot Jr.", WeaponClass.Shooter, SubWeapon.SplatBomb, SpecialWeapon.InkArmor),
	CustomSplattershotJr("Custom Splattershot Jr.", WeaponClass.Shooter, SubWeapon.Autobomb, SpecialWeapon.InkStorm),
	KensaSplattershotJr("Kensa Splattershot Jr.", WeaponClass.Shooter, SubWeapon.Torpedo, SpecialWeapon.BubbleBlower),

	SplashOMatic("Splash-o-matic", WeaponClass.Shooter, SubWeapon.ToxicMist, SpecialWeapon.Inkjet),
	NeoSplashOMatic("Neo Splash-o-matic", WeaponClass.Shooter, SubWeapon.BurstBomb, SpecialWeapon.BombLauncher),

	AeroSprayMg("Aerospray MG", WeaponClass.Shooter, SubWeapon.SuctionBomb, SpecialWeapon.BombLauncher),
	AeroSprayRg("Aerospray RG", WeaponClass.Shooter, SubWeapon.Sprinkler, SpecialWeapon.Baller),
	AeroSprayPg("Aerospray PG", WeaponClass.Shooter, SubWeapon.BurstBomb, SpecialWeapon.BooyahBomb),

	Splattershot("Splattershot", WeaponClass.Shooter, SubWeapon.BurstBomb, SpecialWeapon.Splashdown),
	TentatekSplattershot("Tentatek Splattershot", WeaponClass.Shooter, SubWeapon.SplatBomb, SpecialWeapon.Inkjet),
	KensaSplattershot("Kensa Splattershot", WeaponClass.Shooter, SubWeapon.SuctionBomb, SpecialWeapon.TentaMissiles),
	HeroShotReplica("Hero Shot Replica", WeaponClass.Shooter, SubWeapon.BurstBomb, SpecialWeapon.Splashdown),
	OctoShotReplica("Octo Shot Replica", WeaponClass.Shooter, SubWeapon.SplatBomb, SpecialWeapon.Inkjet),

	Gal52(".52 Gal", WeaponClass.Shooter, SubWeapon.PointSensor, SpecialWeapon.Baller),
	Gal52Deco(".52 Gal Deco", WeaponClass.Shooter, SubWeapon.CurlingBomb, SpecialWeapon.StingRay),
	KensaGal52("Kensa .52 Gal", WeaponClass.Shooter, SubWeapon.SplashWall, SpecialWeapon.BooyahBomb),

	NZap85("N-ZAP '85", WeaponClass.Shooter, SubWeapon.SuctionBomb, SpecialWeapon.InkArmor),
	NZap89("N-ZAP '89", WeaponClass.Shooter, SubWeapon.Autobomb, SpecialWeapon.TentaMissiles),
	NZap83("N-ZAP '83", WeaponClass.Shooter, SubWeapon.Sprinkler, SpecialWeapon.InkStorm),

	SplattershotPro("Splattershot Pro", WeaponClass.Shooter, SubWeapon.PointSensor, SpecialWeapon.InkStorm),
	ForgeSplattershotPro("Forge Splattershot Pro", WeaponClass.Shooter, SubWeapon.SuctionBomb, SpecialWeapon.BubbleBlower),
	KensaSplattershotPro("Kensa Splattershot Pro", WeaponClass.Shooter, SubWeapon.SplatBomb, SpecialWeapon.BooyahBomb),

	Gal96(".96 Gal", WeaponClass.Shooter, SubWeapon.Sprinkler, SpecialWeapon.InkArmor),
	Gal96Deco(".96 Gal Deco", WeaponClass.Shooter, SubWeapon.SplashWall, SpecialWeapon.Splashdown),

	JetSquelcher("Jet Squelcher", WeaponClass.Shooter, SubWeapon.ToxicMist, SpecialWeapon.TentaMissiles),
	CustomJetSquelcher("Custom Jet Squelcher", WeaponClass.Shooter, SubWeapon.BurstBomb, SpecialWeapon.StingRay),


	// Blasters
	LunaBlaster("Luna Blaster", WeaponClass.Blaster, SubWeapon.SplatBomb, SpecialWeapon.Baller),
	LunaBlasterNeo("Luna Blaster Neo", WeaponClass.Blaster, SubWeapon.InkMine, SpecialWeapon.BombLauncher),
	KensaLunaBlaster("Kensa Luna Blaster", WeaponClass.Blaster, SubWeapon.FizzyBomb, SpecialWeapon.InkStorm),

	Blaster("Blaster", WeaponClass.Blaster, SubWeapon.ToxicMist, SpecialWeapon.Splashdown),
	CustomBlaster("Custom Blaster", WeaponClass.Blaster, SubWeapon.Autobomb, SpecialWeapon.Inkjet),
	HeroBlasterReplica("Hero Blaster Replica", WeaponClass.Blaster, SubWeapon.ToxicMist, SpecialWeapon.Splashdown),

	RangeBlaster("Range Blaster", WeaponClass.Blaster, SubWeapon.SuctionBomb, SpecialWeapon.InkStorm),
	CustomRangeBlaster("Custom Range Blaster", WeaponClass.Blaster, SubWeapon.CurlingBomb, SpecialWeapon.BubbleBlower),
	GrimRangeBlaster("Grim Range Blaster", WeaponClass.Blaster, SubWeapon.BurstBomb, SpecialWeapon.TentaMissiles),

	ClashBlaster("Clash Blaster", WeaponClass.Blaster, SubWeapon.SplatBomb, SpecialWeapon.StingRay),
	ClashBlasterNeo("Clash Blaster Neo", WeaponClass.Blaster, SubWeapon.CurlingBomb, SpecialWeapon.TentaMissiles),

	RapidBlaster("Rapid Blaster", WeaponClass.Blaster, SubWeapon.InkMine, SpecialWeapon.BombLauncher),
	RapidBlasterDeco("Rapid Blaster Deco", WeaponClass.Blaster, SubWeapon.SuctionBomb, SpecialWeapon.Inkjet),
	KensaRapidBlaster("Kensa Rapid Blaster", WeaponClass.Blaster, SubWeapon.Torpedo, SpecialWeapon.Baller),

	RapidBlasterPro("Rapid Blaster Pro", WeaponClass.Blaster, SubWeapon.ToxicMist, SpecialWeapon.InkStorm),
	RapidBlasterProDeco("Rapid Blaster Pro Deco", WeaponClass.Blaster, SubWeapon.SplashWall, SpecialWeapon.InkArmor),


	// semi automatic
	L3Nozzlenose("L-3 Nozzlenose", WeaponClass.Semi, SubWeapon.CurlingBomb, SpecialWeapon.Baller),
	L3NozzlenoseD("L-3 Nozzlenose D", WeaponClass.Semi, SubWeapon.BurstBomb, SpecialWeapon.Inkjet),
	KensaL3Nozzlenose("Kensa L-3 Nozzlenose", WeaponClass.Semi, SubWeapon.SplashWall, SpecialWeapon.UltraStamp),

	H3Nozzlenose("H-3 Nozzlenose", WeaponClass.Semi, SubWeapon.PointSensor, SpecialWeapon.TentaMissiles),
	H3NozzlenoseD("H-3 Nozzlenose D", WeaponClass.Semi, SubWeapon.SuctionBomb, SpecialWeapon.InkArmor),
	CherryH3Nozzlenose("Cherry H-3 Nozzlenose", WeaponClass.Semi, SubWeapon.SplashWall, SpecialWeapon.BubbleBlower),

	Squeezer("Squeezer", WeaponClass.Semi, SubWeapon.SplashWall, SpecialWeapon.StingRay),
	FoilSqueezer("Foil Squeezer", WeaponClass.Semi, SubWeapon.SplatBomb, SpecialWeapon.BubbleBlower),


	// Rollers
	CarbonRoller("Carbon Roller", WeaponClass.Roller, SubWeapon.Autobomb, SpecialWeapon.InkStorm),
	CarbonRollerDeco("Carbon Roller Deco", WeaponClass.Roller, SubWeapon.BurstBomb, SpecialWeapon.BombLauncher),

	SplatRoller("Splat Roller", WeaponClass.Roller, SubWeapon.CurlingBomb, SpecialWeapon.Splashdown),
	KrakOnSplatRoller("Krak-On Splat Roller", WeaponClass.Roller, SubWeapon.SquidBeakon, SpecialWeapon.Baller),
	KensaSplatRoller("Kensa Splat Roller", WeaponClass.Roller, SubWeapon.SplatBomb, SpecialWeapon.BubbleBlower),
	HeroRollerReplica("Hero Roller Replica", WeaponClass.Roller, SubWeapon.CurlingBomb, SpecialWeapon.Splashdown),

	DynamoRoller("Dynamo Roller", WeaponClass.Roller, SubWeapon.InkMine, SpecialWeapon.StingRay),
	GoldDynamoRoller("Gold Dynamo Roller", WeaponClass.Roller, SubWeapon.SplatBomb, SpecialWeapon.InkArmor),
	KensaDynamoRoller("Kensa Dynamo Roller", WeaponClass.Roller, SubWeapon.Sprinkler, SpecialWeapon.BooyahBomb),

	FlingzaRoller("Flingza Roller", WeaponClass.Roller, SubWeapon.SplashWall, SpecialWeapon.BombLauncher),
	FoilFlingzaRoller("Foil Flingza Roller", WeaponClass.Roller, SubWeapon.SuctionBomb, SpecialWeapon.TentaMissiles),


	// Brushes
	Inkbrush("Inkbrush", WeaponClass.Brush, SubWeapon.SplatBomb, SpecialWeapon.Splashdown),
	InkbrushNouveau("Inkbrush Nouveau", WeaponClass.Brush, SubWeapon.InkMine, SpecialWeapon.Baller),
	PermanentInkbrush("Permanent Inkbrush", WeaponClass.Brush, SubWeapon.Sprinkler, SpecialWeapon.InkArmor),

	Octobrush("Octobrush", WeaponClass.Brush, SubWeapon.Autobomb, SpecialWeapon.Inkjet),
	OctobrushNouveau("Octobrush Nouveau", WeaponClass.Brush, SubWeapon.SquidBeakon, SpecialWeapon.TentaMissiles),
	KensaOctobrush("Kensa Octobrush", WeaponClass.Brush, SubWeapon.SuctionBomb, SpecialWeapon.UltraStamp),
	HeroBrushReplica("Herobrush Replica", WeaponClass.Brush, SubWeapon.Autobomb, SpecialWeapon.Inkjet),


	// Chargers
	ClassicSquiffer("Classic Squiffer", WeaponClass.Charger, SubWeapon.PointSensor, SpecialWeapon.InkArmor),
	NewSquiffer("New Squiffer", WeaponClass.Charger, SubWeapon.Autobomb, SpecialWeapon.Baller),
	FreshSquiffer("Fresh Squiffer", WeaponClass.Charger, SubWeapon.SuctionBomb, SpecialWeapon.Inkjet),

	SplatCharger("Splat Charger", WeaponClass.Charger, SubWeapon.SplatBomb, SpecialWeapon.StingRay),
	FirefinSplatCharger("Firefin Splat Charger", WeaponClass.Charger, SubWeapon.SplashWall, SpecialWeapon.BombLauncher),
	KensaSplatCharger("Kensa Charger", WeaponClass.Charger, SubWeapon.Sprinkler, SpecialWeapon.Baller),
	HeroChargerReplica("Hero Charger Replica", WeaponClass.Charger, SubWeapon.SplatBomb, SpecialWeapon.StingRay),

	Splatterscope("Splatterscope", WeaponClass.Charger, SubWeapon.SplatBomb, SpecialWeapon.StingRay),
	FirefinSplatterscope("Firefin Splatterscope", WeaponClass.Charger, SubWeapon.SplashWall, SpecialWeapon.BombLauncher),
	KensaSplatterscope("Kensa Splatterscope", WeaponClass.Charger, SubWeapon.Sprinkler, SpecialWeapon.Baller),

	Eliter4k("E-liter 4K", WeaponClass.Charger, SubWeapon.InkMine, SpecialWeapon.InkStorm),
	CustomEliter4k("Custom E-liter 4K", WeaponClass.Charger, SubWeapon.SquidBeakon, SpecialWeapon.BubbleBlower),

	Eliter4kScope("E-liter 4K Scope", WeaponClass.Charger, SubWeapon.InkMine, SpecialWeapon.InkStorm),
	CustomEliter4kScope("Custom E-liter 4K Scope", WeaponClass.Charger, SubWeapon.SquidBeakon, SpecialWeapon.BubbleBlower),

	Bamboozler14MkI("Bamboozler 14 Mk I", WeaponClass.Charger, SubWeapon.CurlingBomb, SpecialWeapon.TentaMissiles),
	Bamboozler14MkII("Bamboozler 14 Mk II", WeaponClass.Charger, SubWeapon.ToxicMist, SpecialWeapon.BombLauncher),
	Bamboozler14MkIII("Bamboozler 14 Mk III", WeaponClass.Charger, SubWeapon.FizzyBomb, SpecialWeapon.BubbleBlower),

	GooTuber("Goo Tuber", WeaponClass.Charger, SubWeapon.SuctionBomb, SpecialWeapon.Splashdown),
	CustomGooTuber("Custom Goo Tuber", WeaponClass.Charger, SubWeapon.CurlingBomb, SpecialWeapon.Inkjet),


	// Sloshers
	Slosher("Slosher", WeaponClass.Slosher, SubWeapon.SuctionBomb, SpecialWeapon.TentaMissiles),
	SlosherDeco("Slosher Deco", WeaponClass.Slosher, SubWeapon.Sprinkler, SpecialWeapon.Baller),
	SodaSlosher("Soda Slosher", WeaponClass.Slosher, SubWeapon.SplatBomb, SpecialWeapon.BombLauncher),
	HeroSlosherReplica("Hero Slosher Replica", WeaponClass.Slosher, SubWeapon.SuctionBomb, SpecialWeapon.TentaMissiles),

	TriSlosher("Tri-Slosher", WeaponClass.Slosher, SubWeapon.BurstBomb, SpecialWeapon.InkArmor),
	TriSlosherNouveau("Tri-Slosher Nouveau", WeaponClass.Slosher, SubWeapon.SplatBomb, SpecialWeapon.InkStorm),

	SloshingMachine("Sloshing Machine", WeaponClass.Slosher, SubWeapon.Autobomb, SpecialWeapon.StingRay),
	SloshingMachineNeo("Sloshing Machine Neo", WeaponClass.Slosher, SubWeapon.PointSensor, SpecialWeapon.BombLauncher),
	KensaSloshingMachine("Kensa Sloshing Machine", WeaponClass.Slosher, SubWeapon.FizzyBomb, SpecialWeapon.Splashdown),

	Bloblobber("Bloblobber", WeaponClass.Slosher, SubWeapon.SplashWall, SpecialWeapon.InkStorm),
	BloblobberDeco("Bloblobber Deco", WeaponClass.Slosher, SubWeapon.Sprinkler, SpecialWeapon.BombLauncher),

	Explosher("Explosher", WeaponClass.Slosher, SubWeapon.Sprinkler, SpecialWeapon.BubbleBlower),
	CustomExplosher("Custom Explosher", WeaponClass.Slosher, SubWeapon.PointSensor, SpecialWeapon.Baller),


	// Splatlings
	MiniSplatling("Mini Splatling", WeaponClass.Splatling, SubWeapon.BurstBomb, SpecialWeapon.TentaMissiles),
	ZinkMiniSplatling("Zink Mini Splatling", WeaponClass.Splatling, SubWeapon.CurlingBomb, SpecialWeapon.InkStorm),
	KensaMiniSplatling("Kensa Mini Splatling", WeaponClass.Splatling, SubWeapon.ToxicMist, SpecialWeapon.UltraStamp),

	HeavySplatling("Heavy Splatling", WeaponClass.Splatling, SubWeapon.Sprinkler, SpecialWeapon.StingRay),
	HeavySplatlingDeco("Heavy Splatling Deco", WeaponClass.Splatling, SubWeapon.SplashWall, SpecialWeapon.BubbleBlower),
	HeavySplatlingRemix("Heavy Splatling Remix", WeaponClass.Splatling, SubWeapon.PointSensor, SpecialWeapon.BooyahBomb),
	HeroSplatlingReplica("Hero Splatling Replica", WeaponClass.Splatling, SubWeapon.Sprinkler, SpecialWeapon.StingRay),

	HydraSplatling("Hydra Splatling", WeaponClass.Splatling, SubWeapon.Autobomb, SpecialWeapon.Splashdown),
	CustomHydraSplatling("Custom Hydra Splatling", WeaponClass.Splatling, SubWeapon.InkMine, SpecialWeapon.InkArmor),

	BallpointSplatling("Ballpoint Splatling", WeaponClass.Splatling, SubWeapon.ToxicMist, SpecialWeapon.Inkjet),
	BallpointSplatlingNouveau("Ballpoint Splatling Nouveau", WeaponClass.Splatling, SubWeapon.SquidBeakon, SpecialWeapon.InkStorm),

	Nautilus47("Nautilus 47", WeaponClass.Splatling, SubWeapon.PointSensor, SpecialWeapon.Baller),
	Nautilus79("Nautilus 79", WeaponClass.Splatling, SubWeapon.SuctionBomb, SpecialWeapon.Inkjet),


	// Dualies
	DappleDualies("Dapple Dualies", WeaponClass.Dualies, SubWeapon.SquidBeakon, SpecialWeapon.BombLauncher),
	DappleDualiesNouveau("Dapple Dualies Nouveau", WeaponClass.Dualies, SubWeapon.ToxicMist, SpecialWeapon.InkStorm),
	ClearDappleDualies("Clear Dapple Dualies", WeaponClass.Dualies, SubWeapon.Torpedo, SpecialWeapon.Splashdown),

	SplatDualies("Splat Dualies", WeaponClass.Dualies, SubWeapon.BurstBomb, SpecialWeapon.TentaMissiles),
	EnperrySplatDualies("Enperry Splat Dualies", WeaponClass.Dualies, SubWeapon.CurlingBomb, SpecialWeapon.Inkjet),
	KensaSplatDualies("Kensa Splat Dualies", WeaponClass.Dualies, SubWeapon.SuctionBomb, SpecialWeapon.Baller),
	HeroDualieReplicas("Hero Dualie Replicas", WeaponClass.Dualies, SubWeapon.BurstBomb, SpecialWeapon.TentaMissiles),

	GloogaDualies("Glooga Dualies", WeaponClass.Dualies, SubWeapon.InkMine, SpecialWeapon.Inkjet),
	GloogaDualiesDeco("Glooga Dualies Deco", WeaponClass.Dualies, SubWeapon.SplashWall, SpecialWeapon.Baller),
	KensaGloogaDualies("Kensa Glooga Dualies", WeaponClass.Dualies, SubWeapon.FizzyBomb, SpecialWeapon.InkArmor),

	DualieSquelchers("Dualie Squelchers", WeaponClass.Dualies, SubWeapon.PointSensor, SpecialWeapon.TentaMissiles),
	CustomDualieSquelchers("Custom Dualie Squelchers", WeaponClass.Dualies, SubWeapon.SplatBomb, SpecialWeapon.InkStorm),

	DarkTetraDualies("Dark Tetra Dualies", WeaponClass.Dualies, SubWeapon.Autobomb, SpecialWeapon.Splashdown),
	LightTetraDualies("Light Tetra Dualies", WeaponClass.Dualies, SubWeapon.Sprinkler, SpecialWeapon.BombLauncher),


	// Brellas
	SplatBrella("Splat Brella", WeaponClass.Brella, SubWeapon.Sprinkler, SpecialWeapon.InkStorm),
	SorellaBrella("Sorella Brella", WeaponClass.Brella, SubWeapon.Autobomb, SpecialWeapon.BombLauncher),
	HeroBrellaReplica("Hero Brella Replica", WeaponClass.Brella, SubWeapon.Sprinkler, SpecialWeapon.InkStorm),

	TentaBrella("Tenta Brella", WeaponClass.Brella, SubWeapon.SquidBeakon, SpecialWeapon.BubbleBlower),
	TentaSorellaBrella("Tenta Sorella Brella", WeaponClass.Brella, SubWeapon.SplashWall, SpecialWeapon.BombLauncher),
	TentaCamoBrella("Tenta Camo Brella", WeaponClass.Brella, SubWeapon.InkMine, SpecialWeapon.UltraStamp),

	UndercoverBrella("Undercover Brella", WeaponClass.Brella, SubWeapon.InkMine, SpecialWeapon.Splashdown),
	UndercoverSorellaBrella("Undercover Sorella Brella", WeaponClass.Brella, SubWeapon.SplatBomb, SpecialWeapon.Baller),
	KensaUndercoverBrella("Kensa Undercover Brella", WeaponClass.Brella, SubWeapon.Torpedo, SpecialWeapon.InkArmor);

	private final String name;
	private final WeaponClass weaponClass;
	private final SubWeapon subWeapon;
	private final SpecialWeapon specialWeapon;

	WeaponKit(String name, WeaponClass weaponClass, SubWeapon subWeapon, SpecialWeapon specialWeapon) {
		this.name = name;
		this.weaponClass = weaponClass;
		this.subWeapon = subWeapon;
		this.specialWeapon = specialWeapon;
	}
}

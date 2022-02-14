package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

public enum AbilityType {
	Any("Any"),
	AbilityDoubler("Ability Doubler"),
	BombDefenseUpDx("Bomb Defense Up DX"),
	Comeback("Comeback"),
	DropRoller("Drop Roller"),
	Haunt("Haunt"),
	InkRecoveryUp("Ink Recovery Up"),
	InkResistanceUp("Ink Resistance Up"),
	InkSaverMain("Ink Saver (Main)"),
	InkSaverSub("Ink Saver (Sub)"),
	LastDitchEffort("Last-Ditch Effort"),
	MainPowerUp("Main Power Up"),
	NinjaSquid("Ninja Squid"),
	ObjectShredder("Object Shredder"),
	OpeningGambit("Opening Gambit"),
	QuickRespawn("Quick Respawn"),
	QuickSuperJump("Quick Super Jump"),
	RespawnPunisher("Respawn Punisher"),
	RunSpeedUp("Run Speed Up"),
	SpecialChargeUp("Special Charge Up"),
	SpecialPowerUp("Special Power Up"),
	SpecialSaver("Special Saver"),
	StealthJump("Stealth Jump"),
	SubPowerUp("Sub Power Up"),
	SwimSpeedUp("Swim Speed Up"),
	Tenacity("Tenacity"),
	ThermalInk("Thermal Ink");

	private final String name;

	AbilityType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}

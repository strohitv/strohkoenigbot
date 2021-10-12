package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.GearType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ManageSplatnetNotificationsAction implements IChatAction {
	private final Map<String, GearType> gearNames = new HashMap<>() {
		{
			put("head", GearType.Head);
			put("h", GearType.Head);
			put("headgear", GearType.Head);
			put("hat", GearType.Head);
			put("cap", GearType.Head);

			put("clothes", GearType.Shirt);
			put("c", GearType.Shirt);
			put("clothing", GearType.Shirt);
			put("shirt", GearType.Shirt);
			put("body", GearType.Shirt);

			put("shoes", GearType.Shoes);
			put("s", GearType.Shoes);
			put("foot", GearType.Shoes);
			put("feet", GearType.Shoes);
		}
	};

	private final Map<String, AbilityType> abilityNames = new HashMap<>() {
		{
			put("ability doubler", AbilityType.AbilityDoubler);
			put("ability double", AbilityType.AbilityDoubler);
			put("ability", AbilityType.AbilityDoubler);
			put("ad", AbilityType.AbilityDoubler);

			put("bomb defense up dx", AbilityType.BombDefenseUpDx);
			put("bomb defense up", AbilityType.BombDefenseUpDx);
			put("bomb defense", AbilityType.BombDefenseUpDx);
			put("bombdefense", AbilityType.BombDefenseUpDx);
			put("bomb", AbilityType.BombDefenseUpDx);
			put("bdx", AbilityType.BombDefenseUpDx);
			put("bd", AbilityType.BombDefenseUpDx);

			put("comeback", AbilityType.Comeback);
			put("come back", AbilityType.Comeback);

			put("drop roller", AbilityType.DropRoller);
			put("droproller", AbilityType.DropRoller);
			put("dr", AbilityType.DropRoller);

			put("haunt", AbilityType.Haunt);

			put("ink recovery up", AbilityType.InkRecoveryUp);
			put("ink recovery", AbilityType.InkRecoveryUp);
			put("recovery", AbilityType.InkRecoveryUp);

			put("ink resistance up", AbilityType.InkResistanceUp);
			put("ink resistance", AbilityType.InkResistanceUp);
			put("resistance", AbilityType.InkResistanceUp);
			put("ink res", AbilityType.InkResistanceUp);
			put("ink", AbilityType.InkResistanceUp);
			put("iru", AbilityType.InkRecoveryUp);
			put("ir", AbilityType.InkRecoveryUp);

			put("ink saver (main)", AbilityType.InkSaverMain);
			put("ink saver main", AbilityType.InkSaverMain);
			put("inksaver (main)", AbilityType.InkSaverMain);
			put("inksaver main", AbilityType.InkSaverMain);
			put("main saver", AbilityType.InkSaverMain);
			put("mainsaver", AbilityType.InkSaverMain);
			put("ism", AbilityType.InkSaverMain);

			put("ink saver (sub)", AbilityType.InkSaverMain);
			put("ink saver sub", AbilityType.InkSaverMain);
			put("inksaver (sub)", AbilityType.InkSaverMain);
			put("inksaver sub", AbilityType.InkSaverMain);
			put("sub saver", AbilityType.InkSaverMain);
			put("subsaver", AbilityType.InkSaverMain);
			put("iss", AbilityType.InkSaverMain);

			put("last ditch effort", AbilityType.LastDitchEffort);
			put("last ditch", AbilityType.LastDitchEffort);
			put("last effort", AbilityType.LastDitchEffort);
			put("last", AbilityType.LastDitchEffort);
			put("ditch", AbilityType.LastDitchEffort);
			put("ditch effort", AbilityType.LastDitchEffort);
			put("effort", AbilityType.LastDitchEffort);
			put("lde", AbilityType.LastDitchEffort);

			put("main power up", AbilityType.MainPowerUp);
			put("mainpower up", AbilityType.MainPowerUp);
			put("mainpowerup", AbilityType.MainPowerUp);
			put("main powerup", AbilityType.MainPowerUp);
			put("main power", AbilityType.MainPowerUp);
			put("mainpower", AbilityType.MainPowerUp);
			put("power up", AbilityType.MainPowerUp);
			put("powerup", AbilityType.MainPowerUp);
			put("main up", AbilityType.MainPowerUp);
			put("mainup", AbilityType.MainPowerUp);
			put("mpu", AbilityType.MainPowerUp);

			put("ninja squid", AbilityType.NinjaSquid);
			put("ninjasquid", AbilityType.NinjaSquid);
			put("ninja", AbilityType.NinjaSquid);
			put("squid", AbilityType.NinjaSquid);
			put("ns", AbilityType.NinjaSquid);

			put("object shredder", AbilityType.ObjectShredder);
			put("objectshredder", AbilityType.ObjectShredder);
			put("object", AbilityType.ObjectShredder);
			put("shredder", AbilityType.ObjectShredder);
			put("os", AbilityType.ObjectShredder);

			put("opening gambit", AbilityType.OpeningGambit);
			put("openinggambit", AbilityType.ObjectShredder);
			put("opening", AbilityType.ObjectShredder);
			put("gambit", AbilityType.ObjectShredder);
			put("og", AbilityType.ObjectShredder);

			put("quick respawn", AbilityType.QuickRespawn);
			put("quickrespawn", AbilityType.QuickRespawn);
			put("quick", AbilityType.QuickRespawn);
			put("qr", AbilityType.QuickRespawn);

			put("quick super jump", AbilityType.QuickSuperJump);
			put("quick superjump", AbilityType.QuickSuperJump);
			put("quicksuperjump", AbilityType.QuickSuperJump);
			put("super jump", AbilityType.QuickSuperJump);
			put("superjump", AbilityType.QuickSuperJump);
			put("quickjump", AbilityType.QuickSuperJump);
			put("qsj", AbilityType.QuickSuperJump);

			put("respawn punisher", AbilityType.RespawnPunisher);
			put("respawnpunisher", AbilityType.RespawnPunisher);
			put("respawn", AbilityType.RespawnPunisher);
			put("punisher", AbilityType.RespawnPunisher);
			put("rp", AbilityType.RespawnPunisher);

			put("run speed up", AbilityType.RunSpeedUp);
			put("runspeed up", AbilityType.RunSpeedUp);
			put("run speedup", AbilityType.RunSpeedUp);
			put("runspeedup", AbilityType.RunSpeedUp);
			put("run speed", AbilityType.RunSpeedUp);
			put("runspeed", AbilityType.RunSpeedUp);
			put("run up", AbilityType.RunSpeedUp);
			put("runup", AbilityType.RunSpeedUp);
			put("rsu", AbilityType.RunSpeedUp);
			put("rs", AbilityType.RunSpeedUp);

			put("special charge up", AbilityType.SpecialChargeUp);
			put("specialcharge up", AbilityType.SpecialChargeUp);
			put("special chargeup", AbilityType.SpecialChargeUp);
			put("specialchargeup", AbilityType.SpecialChargeUp);
			put("special charge", AbilityType.SpecialChargeUp);
			put("specialcharge", AbilityType.SpecialChargeUp);
			put("special up", AbilityType.SpecialChargeUp);
			put("charge up", AbilityType.SpecialChargeUp);
			put("scu", AbilityType.SpecialChargeUp);
			put("sc", AbilityType.SpecialChargeUp);

			put("special power up", AbilityType.SpecialPowerUp);
			put("specialpower up", AbilityType.SpecialPowerUp);
			put("special powerup", AbilityType.SpecialPowerUp);
			put("specialpowerup", AbilityType.SpecialPowerUp);
			put("special power", AbilityType.SpecialPowerUp);
			put("specialpower", AbilityType.SpecialPowerUp);
			put("spu", AbilityType.SpecialPowerUp);
			put("sp", AbilityType.SpecialPowerUp);

			put("special saver", AbilityType.SpecialSaver);
			put("specialsaver", AbilityType.SpecialSaver);
			put("saver", AbilityType.SpecialSaver);
			put("ss", AbilityType.SpecialSaver);

			put("stealth jump", AbilityType.StealthJump);
			put("stealthjump", AbilityType.StealthJump);
			put("stealth", AbilityType.StealthJump);
			put("jump", AbilityType.StealthJump);
			put("sj", AbilityType.StealthJump);

			put("sub power up", AbilityType.SubPowerUp);
			put("subpower up", AbilityType.SubPowerUp);
			put("sub powerup", AbilityType.SubPowerUp);
			put("subpowerup", AbilityType.SubPowerUp);
			put("sub up", AbilityType.SubPowerUp);
			put("sub power", AbilityType.SubPowerUp);
			put("subpower", AbilityType.SubPowerUp);
			put("sub", AbilityType.SubPowerUp);

			put("swim speed up", AbilityType.SwimSpeedUp);
			put("swimspeed up", AbilityType.SwimSpeedUp);
			put("swim speedup", AbilityType.SwimSpeedUp);
			put("swimspeedup", AbilityType.SwimSpeedUp);
			put("swim speed", AbilityType.SwimSpeedUp);
			put("swimspeed", AbilityType.SwimSpeedUp);
			put("swim up", AbilityType.SwimSpeedUp);
			put("swimup", AbilityType.SwimSpeedUp);
			put("ssu", AbilityType.SwimSpeedUp);

			put("tenacity", AbilityType.Tenacity);

			put("thermal ink", AbilityType.ThermalInk);
			put("thermalink", AbilityType.ThermalInk);
			put("thermal", AbilityType.ThermalInk);
			put("ti", AbilityType.ThermalInk);
		}
	};

	private final ArrayList<AbilityType> subAbilities = new ArrayList<>() {
		{
			add(AbilityType.BombDefenseUpDx);
			add(AbilityType.InkRecoveryUp);
			add(AbilityType.InkResistanceUp);
			add(AbilityType.InkSaverMain);
			add(AbilityType.InkSaverSub);
			add(AbilityType.MainPowerUp);
			add(AbilityType.QuickRespawn);
			add(AbilityType.QuickSuperJump);
			add(AbilityType.RunSpeedUp);
			add(AbilityType.SpecialChargeUp);
			add(AbilityType.SpecialSaver);
			add(AbilityType.SpecialPowerUp);
			add(AbilityType.SubPowerUp);
			add(AbilityType.SwimSpeedUp);
		}
	};

	private final Map<AbilityType, GearType> exclusiveAbilities = new HashMap<>() {
		{
			put(AbilityType.Comeback, GearType.Head);
			put(AbilityType.LastDitchEffort, GearType.Head);
			put(AbilityType.OpeningGambit, GearType.Head);
			put(AbilityType.Tenacity, GearType.Head);

			put(AbilityType.AbilityDoubler, GearType.Shirt);
			put(AbilityType.Haunt, GearType.Shirt);
			put(AbilityType.NinjaSquid, GearType.Shirt);
			put(AbilityType.RespawnPunisher, GearType.Shirt);
			put(AbilityType.ThermalInk, GearType.Shirt);

			put(AbilityType.DropRoller, GearType.Shoes);
			put(AbilityType.ObjectShredder, GearType.Shoes);
			put(AbilityType.StealthJump, GearType.Shoes);
		}
	};

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage);
	}

	@Override
	public void run(ActionArgs args) {
		// todo: check for user role because of rate limiting https://dev.twitch.tv/docs/irc/guide
		// todo: maybe sending messages out via discord? probably not, because people could troll and spam other discord account using that.
		// todo: maybe only allow it to subs? Don't think I'll ever reach the rate limit if I only use it for subs.
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		boolean remove = false;
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (!message.startsWith("!notify") && !(remove = message.startsWith("!unnotify"))) {
			return;
		}

		if (remove) {
			message = message.substring("!unnotify".length()).trim();
		} else {
			message = message.substring("!notify".length()).trim();
		}

		ArrayList<String> list = Arrays.stream(message.split(" ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));

		for (int i = 0; i < list.size(); i++) {
			while (i < list.size() - 1 && abilityNames.containsKey(String.format("%s %s", list.get(i), list.get(i + 1)))) {
				list.set(i, String.format("%s %s", list.get(i), list.get(i + 1)));
				list.remove(i + 1);
			}

			if (!abilityNames.containsKey(list.get(i)) && !gearNames.containsKey(list.get(i))) {
				list.remove(i);
				i--;
			}
		}

		GearType type = list.stream().filter(gearNames::containsKey).map(gearNames::get).findFirst().orElse(GearType.Any);

		String mainString = list.stream().filter(abilityNames::containsKey).findFirst().orElse("any");
		AbilityType main = abilityNames.get(mainString);
		list.remove(mainString);

		AbilityType favored = list.stream()
				.filter(abilityNames::containsKey)
				.map(abilityNames::get)
				.filter(subAbilities::contains)
				.findFirst()
				.orElse(AbilityType.Any);

		if (type != GearType.Any && exclusiveAbilities.get(main) != type) {
			// ERROR -> This ability cannot be a main ability on that gear type
			return;
		}

		// todo do create or remove operation in database
		// todo make sure to use twitch account id so it won't fail when they change their username
		System.out.printf("%s %s %s%n", type, main, favored);
	}
}

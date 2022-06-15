package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.GearType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2AbilityNotification;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.Splatoon2AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;

import java.util.*;
import java.util.stream.Collectors;

import static tv.strohi.twitch.strohkoenigbot.utils.ParseUtils.parseLongSafe;

@Component
public class ManageSplatnetNotificationsAction extends ChatAction {
	private final Map<String, GearType> gearNames = new HashMap<>();

	private final Map<String, AbilityType> abilityNames = new HashMap<>();

	private final Map<String, AbilityType> abilityNamesPlusAnyAbility = new HashMap<>();

	private final Map<AbilityType, GearType> exclusiveAbilities = new HashMap<>();

	private final Splatoon2AbilityNotificationRepository splatoon2AbilityNotificationRepository;
	private final AccountRepository accountRepository;

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Autowired
	public ManageSplatnetNotificationsAction(Splatoon2AbilityNotificationRepository splatoon2AbilityNotificationRepository, AccountRepository accountRepository) {
		this.splatoon2AbilityNotificationRepository = splatoon2AbilityNotificationRepository;
		this.accountRepository = accountRepository;

		fillGearNames();
		fillAbilityNames();
		fillAbilityNamesPlusAnyAbility();
		fillExclusiveAbilities();
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	@Override
	public void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		boolean remove = false;
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!splatnet notifications")) {
			// Send current notifications of the account via discord
			Account account = accountRepository.findByDiscordIdOrderById(parseLongSafe(args.getUserId()))
					.stream()
					.findFirst()
					.orElse(null);

			if (account == null) {
				sender.send("You don't have any registered notifications yet. Use the !notify command to add some!");
				return;
			}

			List<Splatoon2AbilityNotification> notifications = splatoon2AbilityNotificationRepository.findByDiscordIdOrderById(account.getId());
			if (notifications.size() > 0) {
				StringBuilder builder = new StringBuilder("**The following notifications are registered for your channel**:");
				for (Splatoon2AbilityNotification notification : notifications) {
					builder.append(String.format("\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored())));
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), builder.toString());
			} else {
				sender.send("You don't have any registered notifications yet. Use the !notify command to add some!");
			}

			return;
		}

		if (!message.startsWith("!splatnet notify") && !(remove = message.startsWith("!splatnet unnotify"))) {
			return;
		}

		Long discordId = accountRepository.findByDiscordIdOrderById(Long.parseLong(args.getUserId())).stream()
				.map(Account::getId)
				.findFirst()
				.orElse(null);

		if (discordId == null) {
			Account account = new Account();
			account.setDiscordId(Long.parseLong(args.getUserId()));

			account = accountRepository.save(account);
			discordId = account.getId();
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

			if (!abilityNamesPlusAnyAbility.containsKey(list.get(i)) && !gearNames.containsKey(list.get(i))) {
				list.remove(i);
				i--;
			}
		}

		GearType type = list.stream().filter(gearNames::containsKey).map(gearNames::get).findFirst().orElse(GearType.Any);
		if (type == GearType.Any) {
			list.remove("any");
		}

		ArrayList<AbilityType> abilities = list.stream()
				.filter(abilityNamesPlusAnyAbility::containsKey)
				.map(abilityNamesPlusAnyAbility::get)
				.collect(Collectors.toCollection(ArrayList::new));

		AbilityType main = abilities.stream()
				.filter(exclusiveAbilities::containsKey)
				.findFirst()
				.orElse(abilities.stream()
						.findFirst()
						.orElse(AbilityType.Any));

		abilities.remove(main);

		AbilityType favored = abilities.stream()
				.filter(a -> !exclusiveAbilities.containsKey(a))
				.findFirst()
				.orElse(AbilityType.Any);

		if (main == favored && main != AbilityType.Any) {
			// ERROR -> Main and favored ability of a shirt do never equal.
			sender.send(String.format(
					"ERROR! Your search for %s with %s and %s is invalid because gear cannot have the same main and favored ability!",
					getGearString(type),
					getAbilityString(main, false),
					getAbilityString(favored, true))
			);
			return;
		}

		if (type != GearType.Any && main != AbilityType.Any && exclusiveAbilities.containsKey(main) && exclusiveAbilities.get(main) != type) {
			// ERROR -> This ability cannot be a main ability on that gear type
			sender.send(String.format(
					"ERROR! Your search for %s with %s is invalid because such gear does not exist!",
					getGearString(type),
					getAbilityString(main, false))
			);
			return;
		}

		if (!remove) {
			Splatoon2AbilityNotification notification = new Splatoon2AbilityNotification();
			notification.setDiscordId(discordId);
			notification.setGear(type);
			notification.setMain(main);
			notification.setFavored(favored);

			splatoon2AbilityNotificationRepository.save(notification);

			sender.send(String.format(
					"Alright! I'm going to notify you via private message when I find %s with %s and %s in SplatNet shop.",
					getGearString(type),
					getAbilityString(main, false),
					getAbilityString(favored, true))
			);

			accountRepository.findById(discordId).stream()
					.map(Account::getDiscordId)
					.findFirst()
					.ifPresent(discordAccountId -> discordBot.sendPrivateMessage(discordAccountId, String.format("**The following notification has been added due to your request**:\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored()))));
		} else {
			List<Splatoon2AbilityNotification> notifications = splatoon2AbilityNotificationRepository.findByDiscordIdOrderById(discordId);

			ArrayList<Long> idList = Arrays.stream(message.split(" "))
					.map(String::trim)
					.filter(id -> id.matches("[0-9]+"))
					.map(Long::parseLong)
					.filter(id -> notifications.stream().anyMatch(notif -> notif.getId() == id))
					.collect(Collectors.toCollection(ArrayList::new));

			if (notifications.size() > 0) {
				if (idList.size() == 0) {
					splatoon2AbilityNotificationRepository.deleteAll(notifications);

					sender.send("Alright! I'm not going to notify you anymore when I find any gear in SplatNet shop.");
				} else {
					StringBuilder builder = new StringBuilder("**The following notifications have been removed due to your request**:");
					for (Splatoon2AbilityNotification notification : notifications.stream().filter(notif -> idList.contains(notif.getId())).collect(Collectors.toList())) {
						builder.append(String.format("\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored())));
					}

					splatoon2AbilityNotificationRepository.deleteAllById(idList);

					sender.send("Alright! I'm not going to notify you anymore when I find any gear with the specified ids in SplatNet shop.");
					accountRepository.findById(discordId).stream()
							.map(Account::getDiscordId)
							.findFirst()
							.ifPresent(discordAccountId -> discordBot.sendPrivateMessage(discordAccountId, builder.toString()));
				}
			} else {
				sender.send("You didn't have any notifications to remove.");
			}
		}
	}

	private String getGearString(GearType type) {
		String result;

		switch (type) {
			case Head:
				result = "headgear";
				break;
			case Shirt:
				result = "shirts";
				break;
			case Shoes:
				result = "shoes";
				break;
			case Any:
			default:
				result = "any gear";
				break;
		}

		return result;
	}

	private String getAbilityString(AbilityType type, boolean favoredAbility) {
		String result;

		if (type == AbilityType.Any) {
			result = String.format("any %s ability", favoredAbility ? "favored" : "main");
		} else {
			result = type.getName();
			result = String.format("%s as %s ability", result, favoredAbility ? "favored" : "main");
		}

		return result;
	}

	private String getAbilityString(AbilityType type) {
		String result;

		if (type == AbilityType.Any) {
			result = "Any";
		} else {
			result = type.getName();
		}

		return result;
	}

	private void fillGearNames() {
		// gear names
		gearNames.put("head", GearType.Head);
		gearNames.put("h", GearType.Head);
		gearNames.put("headgear", GearType.Head);
		gearNames.put("hat", GearType.Head);
		gearNames.put("cap", GearType.Head);

		gearNames.put("clothes", GearType.Shirt);
		gearNames.put("c", GearType.Shirt);
		gearNames.put("clothing", GearType.Shirt);
		gearNames.put("shirt", GearType.Shirt);
		gearNames.put("body", GearType.Shirt);

		gearNames.put("shoes", GearType.Shoes);
		gearNames.put("s", GearType.Shoes);
		gearNames.put("foot", GearType.Shoes);
		gearNames.put("feet", GearType.Shoes);
	}

	private void fillAbilityNames() {
		// ability names
		abilityNames.put("ability doubler", AbilityType.AbilityDoubler);
		abilityNames.put("ability double", AbilityType.AbilityDoubler);
		abilityNames.put("ability", AbilityType.AbilityDoubler);
		abilityNames.put("doubler", AbilityType.AbilityDoubler);
		abilityNames.put("ad", AbilityType.AbilityDoubler);

		abilityNames.put("bomb defense up dx", AbilityType.BombDefenseUpDx);
		abilityNames.put("bomb defense up", AbilityType.BombDefenseUpDx);
		abilityNames.put("bomb defense dx", AbilityType.BombDefenseUpDx);
		abilityNames.put("bomb defense", AbilityType.BombDefenseUpDx);
		abilityNames.put("bombdefense", AbilityType.BombDefenseUpDx);
		abilityNames.put("bomb", AbilityType.BombDefenseUpDx);
		abilityNames.put("bdx", AbilityType.BombDefenseUpDx);
		abilityNames.put("bd", AbilityType.BombDefenseUpDx);

		abilityNames.put("comeback", AbilityType.Comeback);
		abilityNames.put("come back", AbilityType.Comeback);

		abilityNames.put("drop roller", AbilityType.DropRoller);
		abilityNames.put("droproller", AbilityType.DropRoller);
		abilityNames.put("drop", AbilityType.DropRoller);
		abilityNames.put("roller", AbilityType.DropRoller);
		abilityNames.put("dr", AbilityType.DropRoller);

		abilityNames.put("haunt", AbilityType.Haunt);

		abilityNames.put("ink recovery up", AbilityType.InkRecoveryUp);
		abilityNames.put("ink recovery", AbilityType.InkRecoveryUp);
		abilityNames.put("recovery", AbilityType.InkRecoveryUp);

		abilityNames.put("ink resistance up", AbilityType.InkResistanceUp);
		abilityNames.put("ink resistance", AbilityType.InkResistanceUp);
		abilityNames.put("resistance", AbilityType.InkResistanceUp);
		abilityNames.put("ink res", AbilityType.InkResistanceUp);
		abilityNames.put("inkres", AbilityType.InkResistanceUp);
		abilityNames.put("ink", AbilityType.InkResistanceUp);
		abilityNames.put("iru", AbilityType.InkResistanceUp);
		abilityNames.put("ir", AbilityType.InkResistanceUp);

		abilityNames.put("ink saver (main)", AbilityType.InkSaverMain);
		abilityNames.put("ink saver main", AbilityType.InkSaverMain);
		abilityNames.put("inksaver (main)", AbilityType.InkSaverMain);
		abilityNames.put("inksaver main", AbilityType.InkSaverMain);
		abilityNames.put("main saver", AbilityType.InkSaverMain);
		abilityNames.put("mainsaver", AbilityType.InkSaverMain);
		abilityNames.put("ism", AbilityType.InkSaverMain);

		abilityNames.put("ink saver (sub)", AbilityType.InkSaverSub);
		abilityNames.put("ink saver sub", AbilityType.InkSaverSub);
		abilityNames.put("inksaversub", AbilityType.InkSaverSub);
		abilityNames.put("inksaver (sub)", AbilityType.InkSaverSub);
		abilityNames.put("inksaver sub", AbilityType.InkSaverSub);
		abilityNames.put("sub saver", AbilityType.InkSaverSub);
		abilityNames.put("subsaver", AbilityType.InkSaverSub);
		abilityNames.put("iss", AbilityType.InkSaverSub);

		abilityNames.put("last-ditch effort", AbilityType.LastDitchEffort);
		abilityNames.put("last ditch effort", AbilityType.LastDitchEffort);
		abilityNames.put("lastditcheffort", AbilityType.LastDitchEffort);
		abilityNames.put("last ditch", AbilityType.LastDitchEffort);
		abilityNames.put("last effort", AbilityType.LastDitchEffort);
		abilityNames.put("last", AbilityType.LastDitchEffort);
		abilityNames.put("ditch", AbilityType.LastDitchEffort);
		abilityNames.put("ditch effort", AbilityType.LastDitchEffort);
		abilityNames.put("effort", AbilityType.LastDitchEffort);
		abilityNames.put("lde", AbilityType.LastDitchEffort);

		abilityNames.put("main power up", AbilityType.MainPowerUp);
		abilityNames.put("mainpower up", AbilityType.MainPowerUp);
		abilityNames.put("mainpowerup", AbilityType.MainPowerUp);
		abilityNames.put("main powerup", AbilityType.MainPowerUp);
		abilityNames.put("main power", AbilityType.MainPowerUp);
		abilityNames.put("mainpower", AbilityType.MainPowerUp);
		abilityNames.put("power up", AbilityType.MainPowerUp);
		abilityNames.put("powerup", AbilityType.MainPowerUp);
		abilityNames.put("main up", AbilityType.MainPowerUp);
		abilityNames.put("mainup", AbilityType.MainPowerUp);
		abilityNames.put("mpu", AbilityType.MainPowerUp);

		abilityNames.put("ninja squid", AbilityType.NinjaSquid);
		abilityNames.put("ninjasquid", AbilityType.NinjaSquid);
		abilityNames.put("ninja", AbilityType.NinjaSquid);
		abilityNames.put("squid", AbilityType.NinjaSquid);
		abilityNames.put("ns", AbilityType.NinjaSquid);

		abilityNames.put("object shredder", AbilityType.ObjectShredder);
		abilityNames.put("objectshredder", AbilityType.ObjectShredder);
		abilityNames.put("object", AbilityType.ObjectShredder);
		abilityNames.put("shredder", AbilityType.ObjectShredder);
		abilityNames.put("os", AbilityType.ObjectShredder);

		abilityNames.put("opening gambit", AbilityType.OpeningGambit);
		abilityNames.put("openinggambit", AbilityType.OpeningGambit);
		abilityNames.put("opening", AbilityType.OpeningGambit);
		abilityNames.put("gambit", AbilityType.OpeningGambit);
		abilityNames.put("og", AbilityType.OpeningGambit);

		abilityNames.put("quick respawn", AbilityType.QuickRespawn);
		abilityNames.put("quickrespawn", AbilityType.QuickRespawn);
		abilityNames.put("quick", AbilityType.QuickRespawn);
		abilityNames.put("qr", AbilityType.QuickRespawn);

		abilityNames.put("quick super jump", AbilityType.QuickSuperJump);
		abilityNames.put("quick superjump", AbilityType.QuickSuperJump);
		abilityNames.put("quicksuperjump", AbilityType.QuickSuperJump);
		abilityNames.put("super jump", AbilityType.QuickSuperJump);
		abilityNames.put("superjump", AbilityType.QuickSuperJump);
		abilityNames.put("quickjump", AbilityType.QuickSuperJump);
		abilityNames.put("qsj", AbilityType.QuickSuperJump);

		abilityNames.put("respawn punisher", AbilityType.RespawnPunisher);
		abilityNames.put("respawnpunisher", AbilityType.RespawnPunisher);
		abilityNames.put("respawn", AbilityType.RespawnPunisher);
		abilityNames.put("punisher", AbilityType.RespawnPunisher);
		abilityNames.put("rp", AbilityType.RespawnPunisher);

		abilityNames.put("run speed up", AbilityType.RunSpeedUp);
		abilityNames.put("runspeed up", AbilityType.RunSpeedUp);
		abilityNames.put("run speedup", AbilityType.RunSpeedUp);
		abilityNames.put("runspeedup", AbilityType.RunSpeedUp);
		abilityNames.put("run speed", AbilityType.RunSpeedUp);
		abilityNames.put("runspeed", AbilityType.RunSpeedUp);
		abilityNames.put("run up", AbilityType.RunSpeedUp);
		abilityNames.put("runup", AbilityType.RunSpeedUp);
		abilityNames.put("run", AbilityType.RunSpeedUp);
		abilityNames.put("rsu", AbilityType.RunSpeedUp);
		abilityNames.put("rs", AbilityType.RunSpeedUp);

		abilityNames.put("special charge up", AbilityType.SpecialChargeUp);
		abilityNames.put("specialcharge up", AbilityType.SpecialChargeUp);
		abilityNames.put("special chargeup", AbilityType.SpecialChargeUp);
		abilityNames.put("specialchargeup", AbilityType.SpecialChargeUp);
		abilityNames.put("special charge", AbilityType.SpecialChargeUp);
		abilityNames.put("specialcharge", AbilityType.SpecialChargeUp);
		abilityNames.put("special up", AbilityType.SpecialChargeUp);
		abilityNames.put("charge up", AbilityType.SpecialChargeUp);
		abilityNames.put("scu", AbilityType.SpecialChargeUp);
		abilityNames.put("sc", AbilityType.SpecialChargeUp);

		abilityNames.put("special power up", AbilityType.SpecialPowerUp);
		abilityNames.put("specialpower up", AbilityType.SpecialPowerUp);
		abilityNames.put("special powerup", AbilityType.SpecialPowerUp);
		abilityNames.put("specialpowerup", AbilityType.SpecialPowerUp);
		abilityNames.put("special power", AbilityType.SpecialPowerUp);
		abilityNames.put("specialpower", AbilityType.SpecialPowerUp);
		abilityNames.put("spu", AbilityType.SpecialPowerUp);
		abilityNames.put("sp", AbilityType.SpecialPowerUp);

		abilityNames.put("special saver", AbilityType.SpecialSaver);
		abilityNames.put("specialsaver", AbilityType.SpecialSaver);
		abilityNames.put("saver", AbilityType.SpecialSaver);
		abilityNames.put("ss", AbilityType.SpecialSaver);

		abilityNames.put("stealth jump", AbilityType.StealthJump);
		abilityNames.put("stealthjump", AbilityType.StealthJump);
		abilityNames.put("stealth", AbilityType.StealthJump);
		abilityNames.put("jump", AbilityType.StealthJump);
		abilityNames.put("sj", AbilityType.StealthJump);

		abilityNames.put("sub power up", AbilityType.SubPowerUp);
		abilityNames.put("subpower up", AbilityType.SubPowerUp);
		abilityNames.put("sub powerup", AbilityType.SubPowerUp);
		abilityNames.put("subpowerup", AbilityType.SubPowerUp);
		abilityNames.put("sub up", AbilityType.SubPowerUp);
		abilityNames.put("sub power", AbilityType.SubPowerUp);
		abilityNames.put("subpower", AbilityType.SubPowerUp);
		abilityNames.put("sub", AbilityType.SubPowerUp);

		abilityNames.put("swim speed up", AbilityType.SwimSpeedUp);
		abilityNames.put("swimspeed up", AbilityType.SwimSpeedUp);
		abilityNames.put("swim speedup", AbilityType.SwimSpeedUp);
		abilityNames.put("swimspeedup", AbilityType.SwimSpeedUp);
		abilityNames.put("swim speed", AbilityType.SwimSpeedUp);
		abilityNames.put("swimspeed", AbilityType.SwimSpeedUp);
		abilityNames.put("swim up", AbilityType.SwimSpeedUp);
		abilityNames.put("swimup", AbilityType.SwimSpeedUp);
		abilityNames.put("swim", AbilityType.SwimSpeedUp);
		abilityNames.put("ssu", AbilityType.SwimSpeedUp);

		abilityNames.put("tenacity", AbilityType.Tenacity);

		abilityNames.put("thermal ink", AbilityType.ThermalInk);
		abilityNames.put("thermalink", AbilityType.ThermalInk);
		abilityNames.put("thermal", AbilityType.ThermalInk);
		abilityNames.put("ti", AbilityType.ThermalInk);
	}

	private void fillAbilityNamesPlusAnyAbility() {
		// ability names plus 'any' ability
		abilityNamesPlusAnyAbility.putAll(abilityNames);
		abilityNamesPlusAnyAbility.put("any", AbilityType.Any);
	}

	private void fillExclusiveAbilities() {
		// exclusive abilities
		exclusiveAbilities.put(AbilityType.Comeback, GearType.Head);
		exclusiveAbilities.put(AbilityType.LastDitchEffort, GearType.Head);
		exclusiveAbilities.put(AbilityType.OpeningGambit, GearType.Head);
		exclusiveAbilities.put(AbilityType.Tenacity, GearType.Head);

		exclusiveAbilities.put(AbilityType.AbilityDoubler, GearType.Shirt);
		exclusiveAbilities.put(AbilityType.Haunt, GearType.Shirt);
		exclusiveAbilities.put(AbilityType.NinjaSquid, GearType.Shirt);
		exclusiveAbilities.put(AbilityType.RespawnPunisher, GearType.Shirt);
		exclusiveAbilities.put(AbilityType.ThermalInk, GearType.Shirt);

		exclusiveAbilities.put(AbilityType.DropRoller, GearType.Shoes);
		exclusiveAbilities.put(AbilityType.ObjectShredder, GearType.Shoes);
		exclusiveAbilities.put(AbilityType.StealthJump, GearType.Shoes);
	}
}

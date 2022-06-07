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
import tv.strohi.twitch.strohkoenigbot.data.model.DiscordAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.AbilityNotificationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.DiscordAccountRepository;

import java.util.*;
import java.util.stream.Collectors;

import static tv.strohi.twitch.strohkoenigbot.utils.ParseUtils.parseLongSafe;

@Component
public class ManageSplatnetNotificationsAction extends ChatAction {
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
			put("doubler", AbilityType.AbilityDoubler);
			put("ad", AbilityType.AbilityDoubler);

			put("bomb defense up dx", AbilityType.BombDefenseUpDx);
			put("bomb defense up", AbilityType.BombDefenseUpDx);
			put("bomb defense dx", AbilityType.BombDefenseUpDx);
			put("bomb defense", AbilityType.BombDefenseUpDx);
			put("bombdefense", AbilityType.BombDefenseUpDx);
			put("bomb", AbilityType.BombDefenseUpDx);
			put("bdx", AbilityType.BombDefenseUpDx);
			put("bd", AbilityType.BombDefenseUpDx);

			put("comeback", AbilityType.Comeback);
			put("come back", AbilityType.Comeback);

			put("drop roller", AbilityType.DropRoller);
			put("droproller", AbilityType.DropRoller);
			put("drop", AbilityType.DropRoller);
			put("roller", AbilityType.DropRoller);
			put("dr", AbilityType.DropRoller);

			put("haunt", AbilityType.Haunt);

			put("ink recovery up", AbilityType.InkRecoveryUp);
			put("ink recovery", AbilityType.InkRecoveryUp);
			put("recovery", AbilityType.InkRecoveryUp);

			put("ink resistance up", AbilityType.InkResistanceUp);
			put("ink resistance", AbilityType.InkResistanceUp);
			put("resistance", AbilityType.InkResistanceUp);
			put("ink res", AbilityType.InkResistanceUp);
			put("inkres", AbilityType.InkResistanceUp);
			put("ink", AbilityType.InkResistanceUp);
			put("iru", AbilityType.InkResistanceUp);
			put("ir", AbilityType.InkResistanceUp);

			put("ink saver (main)", AbilityType.InkSaverMain);
			put("ink saver main", AbilityType.InkSaverMain);
			put("inksaver (main)", AbilityType.InkSaverMain);
			put("inksaver main", AbilityType.InkSaverMain);
			put("main saver", AbilityType.InkSaverMain);
			put("mainsaver", AbilityType.InkSaverMain);
			put("ism", AbilityType.InkSaverMain);

			put("ink saver (sub)", AbilityType.InkSaverSub);
			put("ink saver sub", AbilityType.InkSaverSub);
			put("inksaversub", AbilityType.InkSaverSub);
			put("inksaver (sub)", AbilityType.InkSaverSub);
			put("inksaver sub", AbilityType.InkSaverSub);
			put("sub saver", AbilityType.InkSaverSub);
			put("subsaver", AbilityType.InkSaverSub);
			put("iss", AbilityType.InkSaverSub);

			put("last-ditch effort", AbilityType.LastDitchEffort);
			put("last ditch effort", AbilityType.LastDitchEffort);
			put("lastditcheffort", AbilityType.LastDitchEffort);
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
			put("openinggambit", AbilityType.OpeningGambit);
			put("opening", AbilityType.OpeningGambit);
			put("gambit", AbilityType.OpeningGambit);
			put("og", AbilityType.OpeningGambit);

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
			put("run", AbilityType.RunSpeedUp);
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
			put("swim", AbilityType.SwimSpeedUp);
			put("ssu", AbilityType.SwimSpeedUp);

			put("tenacity", AbilityType.Tenacity);

			put("thermal ink", AbilityType.ThermalInk);
			put("thermalink", AbilityType.ThermalInk);
			put("thermal", AbilityType.ThermalInk);
			put("ti", AbilityType.ThermalInk);
		}
	};
	private final Map<String, AbilityType> abilityNamesPlusAnyAbility = new HashMap<>() {
		{
			putAll(abilityNames);
			put("any", AbilityType.Any);
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

	private final AbilityNotificationRepository abilityNotificationRepository;
	private final DiscordAccountRepository discordAccountRepository;

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Autowired
	public ManageSplatnetNotificationsAction(AbilityNotificationRepository abilityNotificationRepository, DiscordAccountRepository discordAccountRepository) {
		this.abilityNotificationRepository = abilityNotificationRepository;
		this.discordAccountRepository = discordAccountRepository;
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.PrivateMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	public void execute(ActionArgs args) {
		boolean isTwitchMessage = args.getReason() == TriggerReason.ChatMessage || args.getReason() == TriggerReason.PrivateMessage;
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		boolean remove = false;
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!notifications")) {
			// Send current notifications of the account via discord
			DiscordAccount account = discordAccountRepository.findByDiscordIdOrTwitchUserIdOrderById
					(
							parseLongSafe(args.getUserId()),
							(isTwitchMessage) ? args.getUserId() : null
					)
					.stream()
					.findFirst()
					.orElse(null);

			if (account == null) {
				sender.send("ERROR! You don't have a discord account connected and can't receive any notifications.");
				return;
			}

			List<Splatoon2AbilityNotification> notifications = abilityNotificationRepository.findByDiscordIdOrderById(account.getId());
			if (notifications.size() > 0) {
				StringBuilder builder = new StringBuilder("**The following notifications are registered for your channel**:");
				for (Splatoon2AbilityNotification notification : notifications) {
					builder.append(String.format("\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored())));
				}

				discordBot.sendPrivateMessage(account.getDiscordId(), builder.toString());

				if (isTwitchMessage) {
					sender.send("I've sent you a list with your active notifications on discord.");
				}
			} else {
				sender.send("You didn't register any notifications.");
			}

			return;
		}

		if (!message.startsWith("!notify") && !(remove = message.startsWith("!unnotify"))) {
			return;
		}

		Long discordId;

		if (isTwitchMessage) {
			discordId = discordAccountRepository.findByTwitchUserIdOrderById(args.getUserId()).stream()
					.map(DiscordAccount::getId)
					.findFirst()
					.orElse(null);
		} else {
			discordId = discordAccountRepository.findByDiscordIdOrderById(Long.parseLong(args.getUserId())).stream()
					.map(DiscordAccount::getId)
					.findFirst()
					.orElse(null);
		}

		if (discordId == null) {
			sender.send("ERROR! You need to first connect your discord account which can receive the notification. Please use !connect to connect one first. I don't send notifications to discord accounts which didn't connect first as a spam protection.");
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

		// let's look how it works with vague searches
//		if (!remove && type == GearType.Any && main == AbilityType.Any && favored == AbilityType.Any) {
//			// ERROR -> Too vague
// 			sender.send("ERROR! Your search is too vague! Please specify at least gear, main OR favored ability.");
//			return;
//		}

		// todo do create or remove operation in database
		// todo make sure to use twitch account id so it won't fail when they change their username
		if (!remove) {
			// let's look how it works with no number limit for registered notifications
//			List<AbilityNotification> notifications = abilityNotificationRepository.findByUserId((String) args.getArguments().get(ArgumentKey.ChannelId));
//			if (notifications.size() > 0) {
//				abilityNotificationRepository.deleteAll(notifications);
//			}

			Splatoon2AbilityNotification notification = new Splatoon2AbilityNotification();
			notification.setDiscordId(discordId);
			notification.setGear(type);
			notification.setMain(main);
			notification.setFavored(favored);

			abilityNotificationRepository.save(notification);

			sender.send(String.format(
					"Alright! I'm going to notify you via private message when I find %s with %s and %s in SplatNet shop.",
					getGearString(type),
					getAbilityString(main, false),
					getAbilityString(favored, true))
			);

			discordAccountRepository.findById(discordId).stream()
					.map(DiscordAccount::getDiscordId)
					.findFirst()
					.ifPresent(discordAccountId -> discordBot.sendPrivateMessage(discordAccountId, String.format("**The following notification has been added due to your request**:\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored()))));
		} else {
			List<Splatoon2AbilityNotification> notifications = abilityNotificationRepository.findByDiscordIdOrderById(discordId);

			ArrayList<Long> idList = Arrays.stream(message.split(" "))
					.map(String::trim)
					.filter(id -> id.matches("[0-9]+"))
					.map(Long::parseLong)
					.filter(id -> notifications.stream().anyMatch(notif -> notif.getId() == id))
					.collect(Collectors.toCollection(ArrayList::new));

			if (notifications.size() > 0) {
				if (idList.size() == 0) {
					abilityNotificationRepository.deleteAll(notifications);

					sender.send("Alright! I'm not going to notify you anymore when I find any gear in SplatNet shop.");
				} else {
					StringBuilder builder = new StringBuilder("**The following notifications have been removed due to your request**:");
					for (Splatoon2AbilityNotification notification : notifications.stream().filter(notif -> idList.contains(notif.getId())).collect(Collectors.toList())) {
						builder.append(String.format("\n- Id: **%d** - Gear type: **%s** - Main Ability: **%s** - Favored Ability: **%s**", notification.getId(), notification.getGear(), getAbilityString(notification.getMain()), getAbilityString(notification.getFavored())));
					}

					abilityNotificationRepository.deleteAllById(idList);

					sender.send("Alright! I'm not going to notify you anymore when I find any gear with the specified ids in SplatNet shop.");
					discordAccountRepository.findById(discordId).stream()
							.map(DiscordAccount::getDiscordId)
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
}

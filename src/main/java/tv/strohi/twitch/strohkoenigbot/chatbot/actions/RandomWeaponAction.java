package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.SpecialWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.SubWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponClass;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.weapon.WeaponKit;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RandomWeaponAction extends ChatAction {
	private final Random random = new Random();

	private SplatoonWeaponRepository splatoonWeaponRepository;

	@Autowired
	public void setSplatoonWeaponRepository(SplatoonWeaponRepository splatoonWeaponRepository) {
		this.splatoonWeaponRepository = splatoonWeaponRepository;
	}

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!rw")) {
			List<WeaponKit> kits = new ArrayList<>(WeaponKit.All);

			if (message.contains("100k")) {
				List<SplatoonWeapon> redBadgeWeapons = splatoonWeaponRepository.findByTurfGreaterThanEqual(100_000);
				kits = kits.stream()
						.filter(k -> redBadgeWeapons.stream().noneMatch(rbw -> k.getName().toLowerCase(Locale.ROOT).equals(rbw.getName().toLowerCase(Locale.ROOT))))
						.collect(Collectors.toList());
			}

			List<WeaponClass> chosenClasses = new ArrayList<>();
			for (WeaponClass weaponClass : WeaponClass.All) {
				if (message.contains(weaponClass.getName().toLowerCase(Locale.ROOT))) {
					chosenClasses.add(weaponClass);
				}
			}

			if (chosenClasses.size() > 0) {
				kits = kits.stream().filter(k -> chosenClasses.contains(k.getWeaponClass())).collect(Collectors.toList());
			}

			List<SubWeapon> chosenSubs = new ArrayList<>();
			for (SubWeapon subWeapon : SubWeapon.All) {
				if (message.contains(subWeapon.getName().toLowerCase(Locale.ROOT))) {
					chosenSubs.add(subWeapon);
				}
			}

			if (chosenSubs.size() > 0) {
				kits = kits.stream().filter(k -> chosenSubs.contains(k.getSubWeapon())).collect(Collectors.toList());
			}

			List<SpecialWeapon> chosenSpecials = new ArrayList<>();
			for (SpecialWeapon specialWeapon : SpecialWeapon.All) {
				if (message.contains(specialWeapon.getName().toLowerCase(Locale.ROOT))) {
					chosenSpecials.add(specialWeapon);
				}
			}

			if (chosenSpecials.size() > 0) {
				kits = kits.stream().filter(k -> chosenSpecials.contains(k.getSpecialWeapon())).collect(Collectors.toList());
			}

			if (kits.size() > 0) {
				WeaponKit chosenWeapon = kits.get(random.nextInt(kits.size()));

				String replyMessage = String.format("%s (%s, %s)", chosenWeapon.getName(), chosenWeapon.getSubWeapon().getName(), chosenWeapon.getSpecialWeapon().getName());
				args.getReplySender().send(replyMessage);
			} else {
				args.getReplySender().send("No weapon kit matches your criteria.");
			}
		}
	}
}

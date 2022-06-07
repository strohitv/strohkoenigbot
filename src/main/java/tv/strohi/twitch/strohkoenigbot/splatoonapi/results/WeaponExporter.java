package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetWeapon;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

@Component
public class WeaponExporter {
	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public SplatoonWeapon loadWeapon(SplatNetWeapon splatNetWeapon) {
		SplatoonWeapon weapon = weaponRepository.findBySplatoonApiId(splatNetWeapon.getId());

		if (weapon == null) {
			weapon = new SplatoonWeapon();
			
			weapon.setSplatoonApiId(splatNetWeapon.getId());
			weapon.setName(splatNetWeapon.getName());
			weapon.setImage(splatNetWeapon.getImage());

			weapon.setSubSplatoonApiId(splatNetWeapon.getSub().getId());
			weapon.setSubName(splatNetWeapon.getSub().getName());
			weapon.setSubImage(splatNetWeapon.getSub().getImage_a());

			weapon.setSpecialSplatoonApiId(splatNetWeapon.getSpecial().getId());
			weapon.setSpecialName(splatNetWeapon.getSpecial().getName());
			weapon.setSpecialImage(splatNetWeapon.getSpecial().getImage_a());

			weapon.setTurf(0L);
			weapon.setWins(0);
			weapon.setDefeats(0);

			weapon = weaponRepository.save(weapon);

			discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("New Weapon with id **%d** and Name **%s** was stored into Database!",
							weapon.getId(),
							weapon.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", weapon.getImage()));
		}

		return weapon;
	}
}

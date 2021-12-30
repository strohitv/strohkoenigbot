package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonAbility;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonAbilityRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGearSkill;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

@Component
public class AbilityExporter {
	private SplatoonAbilityRepository abilityRepository;

	@Autowired
	public void setAbilityRepository(SplatoonAbilityRepository abilityRepository) {
		this.abilityRepository = abilityRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public SplatoonAbility loadGear(SplatNetGearSkill splatNetAbility) {
		SplatoonAbility ability = abilityRepository.findBySplatoonApiId(splatNetAbility.getId());

		if (ability == null) {
			ability = new SplatoonAbility();

			ability.setSplatoonApiId(splatNetAbility.getId());
			ability.setName(splatNetAbility.getName());
			ability.setImage(splatNetAbility.getImage());

			ability = abilityRepository.save(ability);

			discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("New Ability with id **%d** and name **%s** was stored into Database!",
							ability.getId(),
							ability.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", ability.getImage()));
		}

		return ability;
	}
}

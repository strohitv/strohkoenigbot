package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Gear;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2GearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGear;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

@Component
public class GearExporter {
	private Splatoon2GearRepository gearRepository;

	@Autowired
	public void setGearRepository(Splatoon2GearRepository gearRepository) {
		this.gearRepository = gearRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public Splatoon2Gear loadGear(SplatNetGear splatNetGear) {
		Splatoon2Gear gear = gearRepository.findBySplatoonApiIdAndKind(splatNetGear.getId(), Splatoon2GearType.getGearTypeByKey(splatNetGear.getKind()));

		if (gear == null) {
			gear = new Splatoon2Gear();

			gear.setSplatoonApiId(splatNetGear.getId());
			gear.setName(splatNetGear.getName());
			gear.setKind(Splatoon2GearType.getGearTypeByKey(splatNetGear.getKind()));
			gear.setImage(splatNetGear.getImage());

			gear = gearRepository.save(gear);

			discordBot.sendServerMessageWithImageUrls(DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("New Gear with id **%d** and Name **%s** was stored into Database!",
							gear.getId(),
							gear.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", gear.getImage()));
		}

		return gear;
	}
}

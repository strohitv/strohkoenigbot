package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonGear;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonGearType;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonGearRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGear;

@Component
public class GearExporter {
	private SplatoonGearRepository gearRepository;

	@Autowired
	public void setGearRepository(SplatoonGearRepository gearRepository) {
		this.gearRepository = gearRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public SplatoonGear loadGear(SplatNetGear splatNetGear) {
		SplatoonGear gear = gearRepository.findBySplatoonApiId(splatNetGear.getId());

		if (gear == null) {
			gear = new SplatoonGear();

			gear.setSplatoonApiId(splatNetGear.getId());
			gear.setName(splatNetGear.getName());
			gear.setKind(SplatoonGearType.getGearTypeByKey(splatNetGear.getKind()));
			gear.setImage(splatNetGear.getImage());

			gear = gearRepository.save(gear);

			discordBot.sendServerMessageWithImages("debug-logs-temp",
					String.format("New Gear with id **%d** and Name **%s** was stored into Database!",
							gear.getId(),
							gear.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", gear.getImage()));
		}

		return gear;
	}
}

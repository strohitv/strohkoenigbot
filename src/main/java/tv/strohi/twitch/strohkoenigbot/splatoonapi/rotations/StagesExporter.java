package tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Stage;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2StageRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStage;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

@Component
public class StagesExporter {
	private Splatoon2StageRepository stageRepository;

	@Autowired
	public void setStageRepository(Splatoon2StageRepository stageRepository) {
		this.stageRepository = stageRepository;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	public Splatoon2Stage loadStage(SplatNetStage splatNetStage) {
		Splatoon2Stage stage = stageRepository.findBySplatoonApiId(splatNetStage.getId());

		if (stage == null) {
			stage = new Splatoon2Stage();

			stage.setSplatoonApiId(splatNetStage.getId());
			stage.setName(splatNetStage.getName());
			stage.setImage(splatNetStage.getImage());

			stage.setZonesWins(0);
			stage.setZonesDefeats(0);
			stage.setRainmakerWins(0);
			stage.setRainmakerDefeats(0);
			stage.setTowerWins(0);
			stage.setTowerDefeats(0);
			stage.setClamsWins(0);
			stage.setClamsDefeats(0);

			stage = stageRepository.save(stage);

			discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("New Stage with id **%d** and Name **%s** was stored into Database!",
							stage.getId(),
							stage.getName()),
					String.format("https://app.splatoon2.nintendo.net%s", stage.getImage()));
		}

		return stage;
	}
}

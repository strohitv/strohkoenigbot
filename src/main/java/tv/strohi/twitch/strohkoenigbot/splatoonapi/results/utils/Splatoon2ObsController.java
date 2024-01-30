package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Rotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2RotationRepository;
import tv.strohi.twitch.strohkoenigbot.obs.ObsController;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetXRankLeaderBoard;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.PeaksExporter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class Splatoon2ObsController {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private Splatoon2MatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(Splatoon2MatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private Splatoon2RotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(Splatoon2RotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private PeaksExporter peaksExporter;

	@Autowired
	public void setPeaksExporter(PeaksExporter peaksExporter) {
		this.peaksExporter = peaksExporter;
	}

	private ObsController obsController;

	@Autowired
	public void setObsSceneSwitcher(ObsController obsController) {
		this.obsController = obsController;
	}

	public void controlOBS(Account account, long startedAt) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		String prefix = configurationRepository.findAllByConfigName(ConfigurationRepository.streamPrefix).stream().map(Configuration::getConfigValue).findFirst().orElse(null);
		logger.info("prefix name: {}", prefix);
		if (prefix != null) {
			String gameSceneName = configurationRepository.findAllByConfigName(prefix + ConfigurationRepository.gameSceneName)
					.stream().map(Configuration::getConfigValue).findFirst().orElse(null);
			String resultsSceneName = configurationRepository.findAllByConfigName(prefix + ConfigurationRepository.resultsSceneName)
					.stream().map(Configuration::getConfigValue).findFirst().orElse(null);

			logger.info("game scene name: {}", gameSceneName);
			logger.info("results scene name: {}", resultsSceneName);

			Splatoon2Rotation rotation = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(Instant.now().getEpochSecond(),
					Instant.now().getEpochSecond(),
					Splatoon2Mode.Ranked);

			SplatNetXRankLeaderBoard leaderBoard = peaksExporter.getLeaderBoard(account, year, month);
			Double currentPower = getCurrentPower(leaderBoard, rotation);
			logger.info("current power: {}", currentPower);
			if (currentPower != null) {
				Splatoon2Match match = matchRepository.findTop1ByAccountIdAndModeAndRuleOrderByStartTimeDesc(account.getId(), Splatoon2Mode.Ranked, rotation.getRule());
				logger.info("match != null: {}", match != null);
				if (match != null) {
					if (match.getXPower() == null
							|| !match.getXPower().equals(currentPower)
							|| matchRepository
							.findByAccountIdAndStartTimeGreaterThanEqualAndMode(account.getId(), startedAt > rotation.getStartTime() ? startedAt : rotation.getStartTime(), Splatoon2Mode.Ranked)
							.size() == 0) {
						logger.info("1 trying to switch to scene: {}", gameSceneName);
						obsController.switchScene(gameSceneName, result -> logger.info("switch successful: {}", result));
					} else {
						logger.info("2 trying to switch to scene: {}", resultsSceneName);
						obsController.switchScene(resultsSceneName, result -> logger.info("switch successful: {}", result));
					}
				}
			} else {
				logger.info("3 trying to switch to scene: {}", gameSceneName);
				obsController.switchScene(gameSceneName, result -> logger.info("switch successful: {}", result));
			}
		}
	}

	private Double getCurrentPower(SplatNetXRankLeaderBoard result, Splatoon2Rotation rotation) {
		Double power = null;

		switch (rotation.getRule()) {
			case Rainmaker:
				if (result.getRainmaker().getMy_ranking() != null) {
					power = result.getRainmaker().getMy_ranking().getX_power();
				}
				break;
			case TowerControl:
				if (result.getTower_control().getMy_ranking() != null) {
					power = result.getTower_control().getMy_ranking().getX_power();
				}
				break;
			case ClamBlitz:
				if (result.getClam_blitz().getMy_ranking() != null) {
					power = result.getClam_blitz().getMy_ranking().getX_power();
				}
				break;
			case SplatZones:
			default:
				if (result.getSplat_zones().getMy_ranking() != null) {
					power = result.getSplat_zones().getMy_ranking().getX_power();
				}
				break;
		}

		return power;
	}
}

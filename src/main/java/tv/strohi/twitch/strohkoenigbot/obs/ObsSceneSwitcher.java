package tv.strohi.twitch.strohkoenigbot.obs;

import net.twasi.obsremotejava.OBSRemoteController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;

import javax.annotation.PreDestroy;

@Component
public class ObsSceneSwitcher {
	private static final Logger logger = LogManager.getLogger(ObsSceneSwitcher.class.getSimpleName());

	private OBSRemoteController controller = null;

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	public void switchScene(String newSceneName) {
		if (controller == null) {
			String obsUrl = configurationRepository.findByConfigName("obsUrl").stream().map(Configuration::getConfigValue).findFirst().orElse(null);
			String obsPassword = configurationRepository.findByConfigName("obsPassword").stream().map(Configuration::getConfigValue).findFirst().orElse(null);

			if (obsUrl == null || obsPassword == null) {
				logger.info("1 connect to OBS failed");
				return;
			}

			controller = new OBSRemoteController(obsUrl, false, obsPassword);

			if (controller.isFailed()) { // Awaits response from OBS
				// Here you can handle a failed connection request
				logger.info("2 connect to OBS failed");
				controller = null;
				return;
			}
		}

		try {
			// Now you can start making requests
			logger.info("Trying to switch scene");
			controller.getScenes((getSceneListResponse -> {
				if ("ok".equals(getSceneListResponse.getStatus()) && getSceneListResponse.getScenes().stream().anyMatch(sc -> sc.getName().equals(newSceneName))) {
					logger.info("scene found, trying to switch");
					controller.setCurrentScene(newSceneName, System.out::println);
				} else {
					logger.warn("scene NOT found");
					logger.warn("status: {}", getSceneListResponse.getStatus());
					logger.warn("error: {}", getSceneListResponse.getError());
					logger.warn("message id: {}", getSceneListResponse.getMessageId());
					if (getSceneListResponse.getScenes() != null) {
						getSceneListResponse.getScenes().forEach(sc -> logger.warn("scene: {}", sc.getName()));
					}
				}
			}));
		} catch (Exception ex) {
			logger.error("Setting scene failed");
			logger.error(ex);
		}
	}

	@PreDestroy
	public void onExit() {
		if (controller != null) {
			try {
				controller.disconnect();
			} catch (Exception ex) {
				logger.error("disconnect obs controller failed");
				logger.error(ex);
			}

			controller = null;
		}
	}
}

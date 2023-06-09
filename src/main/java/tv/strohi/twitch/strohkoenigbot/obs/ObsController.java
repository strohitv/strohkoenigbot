package tv.strohi.twitch.strohkoenigbot.obs;

import io.obswebsocket.community.client.OBSRemoteController;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ObsController {
	private static final Logger logger = LogManager.getLogger(ObsController.class.getSimpleName());

	private static boolean isLive = false;

	public static void setIsLive(boolean isLive) {
		ObsController.isLive = isLive;
	}

	private final ConfigurationRepository configurationRepository;
	private final SchedulingService schedulingService;
	private final List<Consumer<OBSRemoteController>> openCalls = new ArrayList<>();

	private OBSRemoteController controller = null;
	private boolean controllerIsReady = false;
	private boolean shouldResetController = false;

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("ObsController_doCommunication", TickSchedule.getScheduleString(1), this::doControllerCommunication);
	}

	private void doControllerCommunication() {
		if (shouldResetController) {
			logger.info("resetting controller...");
			if (controller != null) {
				controller.disconnect();
				controller = null;
			}

			shouldResetController = false;
		}

		if ((!controllerIsReady || controller == null) && (isLive || openCalls.size() > 0)) {
			logger.info("connecting to controller...");
			connectToController();
		} else if (controllerIsReady && openCalls.size() > 0) {
			logger.info("executing open controller calls...");
			while (openCalls.size() > 0) {
				var call = openCalls.get(0);
				call.accept(controller);
				openCalls.remove(0);
			}
		} else if (!isLive && controllerIsReady && controller != null) {
			shouldResetController = true;
		}
	}

	public void switchScene(String newSceneName, Consumer<Boolean> callback) {
		openCalls.add((obsController) ->
				controller.getSceneList((sceneList) ->
						sceneList.getScenes().stream()
								.filter(scene -> scene.getSceneName().equals(newSceneName))
								.findFirst()
								.ifPresent(scene -> controller.setCurrentProgramScene(newSceneName, result -> callback.accept(result.isSuccessful())))));
	}

	public void changeSourceEnabled(String sourceName, boolean enabled, Consumer<Boolean> callback) {
		openCalls.add((obsController) -> obsController.getCurrentProgramScene((scene) ->
				obsController.getSceneItemId(scene.getCurrentProgramSceneName(), sourceName, 0, idResponse -> {
					if (idResponse.isSuccessful()) {
						obsController.setSceneItemEnabled(scene.getCurrentProgramSceneName(), idResponse.getSceneItemId(), enabled,
								result -> callback.accept(result.isSuccessful()));
					} else {
						obsController.getSceneItemList(scene.getCurrentProgramSceneName(), sceneItemResponse -> {
									if (sceneItemResponse.isSuccessful()) {
										obsController.getGroupList(groups -> {
											if (groups.isSuccessful()) {
												for (var group : groups.getGroups()) {
													if (sceneItemResponse.getSceneItems().stream().anyMatch(sc -> sc.getSourceName().equals(group))) {
														controller.getGroupSceneItemList(group, list -> {
															if (list.isSuccessful()) {
																for (var groupScene : list.getSceneItems()) {
																	if (groupScene.getSourceName().equals(sourceName)) {
																		obsController.setSceneItemEnabled(group, groupScene.getSceneItemId(), enabled,
																				result -> callback.accept(result.isSuccessful()));
																		break;
																	}
																}
															} else {
																callback.accept(false);
															}
														});
													}
												}
											} else {
												callback.accept(false);
											}
										});
									}
								}
						);
					}
				})));
	}

	private void connectToController() {
		String obsUrl = configurationRepository.findByConfigName("obsUrl").stream().map(Configuration::getConfigValue).findFirst().orElse(null);
		String obsPassword = configurationRepository.findByConfigName("obsPassword").stream().map(Configuration::getConfigValue).findFirst().orElse(null);

		if (obsUrl == null || obsPassword == null) {
			logger.info("connect to OBS failed: no credentials found");
			return;
		}

		try {
			var host = obsUrl.split(":")[0];
			var port = obsUrl.split(":")[1];

			controller = OBSRemoteController.builder()
					.host(host)
					.port(Integer.parseInt(port))
					.password(obsPassword)
					.lifecycle()
					.onReady(() -> controllerIsReady = true)
					.onClose(event -> controllerIsReady = false)
					.onDisconnect(() -> controllerIsReady = false)
					.onCommunicatorError(problem -> shouldResetController = true)
					.and()
					.build();

			controller.connect();

			logger.info("issued connection to OBS");
		} catch (Exception e) {
			logger.error(e);
		}
	}
}

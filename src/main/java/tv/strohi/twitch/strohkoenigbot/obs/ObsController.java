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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ObsController {
	private static final Logger logger = LogManager.getLogger(ObsController.class.getSimpleName());
	private static final String OBS_SWITCH_NAME = "obsControllerEnabled";

	private static boolean isLive = false;

	private long failedCount = 1L;
	private Instant nextConnectionAttempt = Instant.now();

	public static void setIsLive(boolean isLive) {
		ObsController.isLive = isLive;
	}

	public static boolean isLive() {
		return ObsController.isLive;
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
		var obsEnabled = configurationRepository.findAllByConfigName(OBS_SWITCH_NAME).stream()
			.map(config -> "1".equals(config.getConfigValue()))
			.findFirst()
			.orElse(false);

		if (!obsEnabled) {
			if (controller != null) {
				controller.disconnect();
				controller.stop();
				controller = null;
			}

			return;
		}

		if (shouldResetController) {
			logger.info("resetting controller...");
			if (controller != null) {
				controller.disconnect();
				controller.stop();
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

	public void setObsEnabled(boolean enabled) {
		var obsEnabledConfig = configurationRepository.findAllByConfigName(OBS_SWITCH_NAME).stream()
			.findFirst()
			.orElse(Configuration.builder().configName(OBS_SWITCH_NAME).build());

		obsEnabledConfig.setConfigValue(enabled ? "1" : "0");

		configurationRepository.save(obsEnabledConfig);
	}

	public void resetFailedCounter() {
		failedCount = 1L;
		nextConnectionAttempt = Instant.now();
	}

	private void connectToController() {
		if (Instant.now().isBefore(nextConnectionAttempt)) {
			return;
		}

		String obsUrl = configurationRepository.findAllByConfigName("obsUrl").stream().map(Configuration::getConfigValue).findFirst().orElse(null);
		String obsPassword = configurationRepository.findAllByConfigName("obsPassword").stream().map(Configuration::getConfigValue).findFirst().orElse(null);

		if (obsUrl == null || obsPassword == null) {
			logger.info("connect to OBS failed: no credentials found");
			return;
		}

		try {
			var host = obsUrl.split(":")[0];
			var port = obsUrl.split(":")[1];

			if (controller != null) {
				try {
					controller.disconnect();
					controller.stop();
					controller = null;
				} catch (RuntimeException e) {
					logger.error("Obs Controller was != null but could not be stopped or disconnected!");
					logger.error(e);
				}
			}

			controller = OBSRemoteController.builder()
				.host(host)
				.port(Integer.parseInt(port))
				.password(obsPassword)
				.connectionTimeout(10)
				.lifecycle()
				.onReady(() -> controllerIsReady = true)
				.onClose(event -> controllerIsReady = false)
				.onDisconnect(() -> controllerIsReady = false)
				.onCommunicatorError(problem -> shouldResetController = true)
				.and()
				.build();

			controller.connect();

			failedCount = 1L;
			nextConnectionAttempt = Instant.now();

			logger.info("issued connection to OBS");
		} catch (Exception e) {
			failedCount *= 2;
			nextConnectionAttempt = Instant.now().plus(failedCount, ChronoUnit.SECONDS);
			logger.error("obs connection failed, setting failedCount to {} seconds, next attempt not before: {}", failedCount, nextConnectionAttempt);
			logger.error(e);
		}
	}
}

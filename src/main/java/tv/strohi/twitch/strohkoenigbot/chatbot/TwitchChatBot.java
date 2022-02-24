package tv.strohi.twitch.strohkoenigbot.chatbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.AutoSoAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class TwitchChatBot {
	private static final Logger logger = LogManager.getLogger(TwitchChatBot.class.getSimpleName());

	private final ResultsExporter resultsExporter;
	private final TwitchAuthRepository twitchAuthRepository;
	private AutoSoAction autoSoAction;
	private static final List<IChatAction> botActions = new ArrayList<>();

	private TwitchBotClient botClient;
	private TwitchBotClient mainAccountClient;

	public static void setBotActions(List<IChatAction> botActions) {
		TwitchChatBot.botActions.clear();
		TwitchChatBot.botActions.addAll(botActions);
	}

	@Autowired
	public TwitchChatBot(ResultsExporter resultsExporter, TwitchAuthRepository twitchAuthRepository, AutoSoAction autoSoAction) {
		this.resultsExporter = resultsExporter;
		this.twitchAuthRepository = twitchAuthRepository;
		this.autoSoAction = autoSoAction;
	}

	@Bean("botClient")
	public TwitchBotClient getBotClient() {
		if (botClient == null) {
			botClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig", autoSoAction);
			twitchAuthRepository.findByIsMain(false).stream().findFirst().ifPresent(auth -> botClient.initializeClient(auth));
		}

		return botClient;
	}

	@Bean("mainAccountClient")
	public TwitchBotClient getMainAccountClient() {
		if (mainAccountClient == null) {
			mainAccountClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig", autoSoAction);
			twitchAuthRepository.findByIsMain(true).stream().findFirst().ifPresent(auth -> mainAccountClient.initializeClient(auth));
		}

		return mainAccountClient;
	}

	public void initializeClients() {
		if (botClient == null) {
			botClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig", autoSoAction);
		}

		if (mainAccountClient == null) {
			mainAccountClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig", autoSoAction);
		}

		List<TwitchAuth> auths = twitchAuthRepository.findAll();

		TwitchAuth botAuth = auths.stream().filter(a -> !a.getIsMain()).findFirst().orElse(null);
		if (botAuth != null) {
			logger.info("found bot client auth, initializing bot client");
			botClient.initializeClient(botAuth);
		}

		TwitchAuth mainAuth = auths.stream().filter(TwitchAuth::getIsMain).findFirst().orElse(null);
		if (mainAuth != null) {
			logger.info("found main client auth, initializing main client");
			mainAccountClient.initializeClient(mainAuth);
		}
	}

	@PreDestroy
	public void stop() {
		logger.info("stopping twitch chat bot");

		if (botClient != null) {
			logger.info("stopping bot client");
			botClient.stop();
		}

		if (mainAccountClient != null) {
			logger.info("stopping main account client");
			mainAccountClient.stop();
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.chatbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class TwitchChatBot {
	private final ResultsExporter resultsExporter;
	private final TwitchAuthRepository twitchAuthRepository;
	private final List<IChatAction> botActions = new ArrayList<>();

	private TwitchBotClient botClient;
	private TwitchBotClient mainAccountClient;

	@Autowired
	public void setBotActions(List<IChatAction> botActions) {
		this.botActions.clear();
		this.botActions.addAll(botActions);
	}

	@Autowired
	public TwitchChatBot(ResultsExporter resultsExporter, TwitchAuthRepository twitchAuthRepository) {
		this.resultsExporter = resultsExporter;
		this.twitchAuthRepository = twitchAuthRepository;
	}

	@Bean("botClient")
	public TwitchBotClient getBotClient() {
		if (botClient == null) {
			botClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig");
			twitchAuthRepository.findByIsMain(false).stream().findFirst().ifPresent(auth -> botClient.initializeClient(auth));
		}

		return botClient;
	}

	@Bean("mainAccountClient")
	public TwitchBotClient getMainAccountClient() {
		if (mainAccountClient == null) {
			mainAccountClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig");
			twitchAuthRepository.findByIsMain(true).stream().findFirst().ifPresent(auth -> mainAccountClient.initializeClient(auth));
		}

		return mainAccountClient;
	}

	public void initializeClients() {
		if (botClient == null) {
			botClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig");
		}

		if (mainAccountClient == null) {
			mainAccountClient = new TwitchBotClient(resultsExporter, botActions, "strohkoenig");
		}

		twitchAuthRepository.findByIsMain(false).stream().findFirst().ifPresent(auth -> botClient.initializeClient(auth));
		twitchAuthRepository.findByIsMain(true).stream().findFirst().ifPresent(auth -> mainAccountClient.initializeClient(auth));
	}

	@PreDestroy
	public void stop() {
		if (botClient != null) {
			botClient.stop();
		}

		if (mainAccountClient != null) {
			mainAccountClient.stop();
		}
	}
}

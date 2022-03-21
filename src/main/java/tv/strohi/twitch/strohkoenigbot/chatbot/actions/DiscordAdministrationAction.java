package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchSoAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchSoAccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;

@Component
public class DiscordAdministrationAction extends ChatAction {
	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	private TwitchAuthRepository authRepository;

	@Autowired
	public void setAuthRepository(TwitchAuthRepository authRepository) {
		this.authRepository = authRepository;
	}

	private ConfigurationRepository configurationRepository;

	private TwitchSoAccountRepository twitchSoAccountRepository;

	@Autowired
	public void setTwitchSoAccountRepository(TwitchSoAccountRepository twitchSoAccountRepository) {
		this.twitchSoAccountRepository = twitchSoAccountRepository;
	}

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private TwitchChatBot twitchChatBot;

	@Autowired
	public void setTwitchChatBot(TwitchChatBot twitchChatBot) {
		this.twitchChatBot = twitchChatBot;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private ResultsExporter resultsExporter;

	@Autowired
	public void setResultsExporter(ResultsExporter resultsExporter) {
		this.resultsExporter = resultsExporter;
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null || !"strohkoenig#8058".equals(args.getUser())) {
			return;
		}

		message = message.toLowerCase().trim();

		if (message.startsWith("!setbottoken")) {
			String newBotToken = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!setbottoken".length()).trim();

			TwitchAuth auth = authRepository.findByIsMain(false).stream().findFirst().orElse(null);
			if (auth != null) {
				auth.setToken(newBotToken);
				authRepository.save(auth);

				twitchChatBot.initializeClients();

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Token was set successfully.");
			}
		} else if (message.startsWith("!start") && !resultsExporter.isStreamRunning()) {
			resultsExporter.start();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Results export started successfully.");
		} else if (message.startsWith("!stop") && resultsExporter.isStreamRunning()) {
			resultsExporter.stop();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Results export stopped successfully.");
		} else if (message.startsWith("!config set") && message.contains("=")) {
			String commandToSet = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config set".length()).trim();

			String propertyName = commandToSet.split("=")[0];
			String propertyValue = commandToSet.substring(propertyName.length() + 1).trim();

			Configuration config = new Configuration();
			config.setConfigName(propertyName);
			List<Configuration> configs = configurationRepository.findByConfigName(propertyName);
			if (configs.size() > 0) {
				config = configs.get(0);
			}

			config.setConfigValue(propertyValue);

			config = configurationRepository.save(config);

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d was stored into database.", config.getId()));
		} else if (message.startsWith("!config get") && !message.toLowerCase().contains("pass")) {
		String propertyName = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config get".length()).trim();

		Configuration config = null;
		List<Configuration> configs = configurationRepository.findByConfigName(propertyName);
		if (configs.size() > 0) {
			config = configs.get(0);
		}

		if (config != null) {
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d: %s", config.getId(), config.getConfigValue()));
		} else {
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Such a configuration does not exist.");
		}

	} else if (message.startsWith("!config remove")) {
			String propertyName = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config remove".length()).trim();

			List<Configuration> configs = configurationRepository.findByConfigName(propertyName);
			if (configs.size() > 0) {
				configurationRepository.deleteAll(configs);
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Configurations were removed from database.");
		} else if (message.startsWith("!platform")) {
			String streamPrefix = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!platform".length()).toLowerCase().trim();

			Configuration config = new Configuration();
			config.setConfigName(ConfigurationRepository.streamPrefix);
			List<Configuration> configs = configurationRepository.findByConfigName(ConfigurationRepository.streamPrefix);
			if (configs.size() > 0) {
				config = configs.get(0);
			}

			config.setConfigValue(streamPrefix);

			config = configurationRepository.save(config);

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d was stored into database.", config.getId()));
		} else if (message.startsWith("!ranked")) {
			String startOrStop = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!ranked".length()).toLowerCase().trim();

			resultsExporter.setRankedRunning("start".equals(startOrStop));

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Ranked running was set to " + "start".equals(startOrStop));
		} else if (message.startsWith("!so add")) {
			String account = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!so add".length()).toLowerCase().trim();

			if (twitchSoAccountRepository.findByUsername(account) == null) {
				TwitchSoAccount soAccount = new TwitchSoAccount();
				soAccount.setUsername(account);
				twitchSoAccountRepository.save(soAccount);
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will trigger an **!so** message whenever **" + account + "** raids or writes the first message in stream.");
		} else if (message.startsWith("!so list")) {
			String answer = "You didn't tell me who to !so yet.";

			List<TwitchSoAccount> accounts = twitchSoAccountRepository.findAll();

			if (accounts.size() > 0) {
				StringBuilder builder = new StringBuilder("**I send an !so command whenever one of the following accounts raids or writes their first message**:\n");
				twitchSoAccountRepository.findAll().forEach(soa -> builder.append("- ").append(soa.getUsername()).append("\n"));

				answer = builder.toString().trim();
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), answer);
		} else if (message.startsWith("!so remove")) {
			String account = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!so remove".length()).toLowerCase().trim();

			TwitchSoAccount soAccount = twitchSoAccountRepository.findByUsername(account);
			if (soAccount != null) {
				twitchSoAccountRepository.delete(soAccount);
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will not trigger an **!so** message anymore whenever **" + account + "** raids or writes the first message in stream.");
		} else if (message.startsWith("!file")) {
			String filepath = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!file".length()).trim();

//			Path path = Paths.get(Paths.get(String.format("%s\\src\\main\\resources\\html\\template-example.html", Paths.get(".").toAbsolutePath().normalize().toString())).getParent().toString(), String.format("%s/win.txt", "/../../shared/woomydx-powers"));
			Path path = Paths.get(String.format("%s/countdown.txt", filepath));
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("resolved path: %s", path.toString()));

			if (Files.exists(path)) {
				try {
					InputStream isPowerGain = new FileInputStream(path.toString());
					String result = new String(isPowerGain.readAllBytes(), StandardCharsets.UTF_8);

					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "File contents:");
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), result);
				} catch (IOException e) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), e.getMessage());
				}
			} else {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "This file does not exist.");
			}
		}
	}
}

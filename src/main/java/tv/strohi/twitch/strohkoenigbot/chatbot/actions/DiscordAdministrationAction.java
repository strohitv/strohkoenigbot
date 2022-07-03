package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchSoAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchSoAccountRepository;
import tv.strohi.twitch.strohkoenigbot.obs.ObsSceneSwitcher;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.StatsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.DailyStatsSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.SplatoonMatchColorComponent;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

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

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private DiscordAccountLoader discordAccountLoader;

	@Autowired
	public void setDiscordAccountLoader(DiscordAccountLoader discordAccountLoader) {
		this.discordAccountLoader = discordAccountLoader;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setTwitchSoAccountRepository(TwitchSoAccountRepository twitchSoAccountRepository) {
		this.twitchSoAccountRepository = twitchSoAccountRepository;
	}

	private TwitchSoAccountRepository twitchSoAccountRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private TwitchMessageSender twitchMessageSender;

	@Autowired
	public void setTwitchMessageSender(TwitchMessageSender twitchMessageSender) {
		this.twitchMessageSender = twitchMessageSender;
	}

	private TwitchBotClient twitchBotClient;

	@Autowired
	public void setTwitchBotClient(TwitchBotClient twitchBotClient) {
		this.twitchBotClient = twitchBotClient;
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

	private StatsExporter statsExporter;

	@Autowired
	public void setStatsExporter(StatsExporter statsExporter) {
		this.statsExporter = statsExporter;
	}

	private SplatoonMatchColorComponent splatoonMatchColorComponent;

	@Autowired
	public void setSplatoonMatchColorComponent(SplatoonMatchColorComponent splatoonMatchColorComponent) {
		this.splatoonMatchColorComponent = splatoonMatchColorComponent;
	}

	private ObsSceneSwitcher obsSceneSwitcher;

	@Autowired
	public void setObsSceneSwitcher(ObsSceneSwitcher obsSceneSwitcher) {
		this.obsSceneSwitcher = obsSceneSwitcher;
	}

	private DailyStatsSender dailyStatsSender;

	@Autowired
	public void setDailyStatsSender(DailyStatsSender dailyStatsSender) {
		this.dailyStatsSender = dailyStatsSender;
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

			TwitchAuth auth = authRepository.findAll().stream().findFirst().orElse(null);
			if (auth != null) {
				auth.setToken(newBotToken);
				authRepository.save(auth);

				twitchBotClient.initializeClient();

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Token was set successfully.");
			}
		} else if (message.startsWith("!start") && !resultsExporter.isStreamRunning()) {
			Account account = accountRepository.findAll().stream()
					.filter(Account::getIsMainAccount)
					.findFirst()
					.orElse(new Account());

			resultsExporter.start(account.getId());
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
		} else if (message.startsWith("!stream ranked")) {
			String startOrStop = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!stream ranked".length()).toLowerCase().trim();

			resultsExporter.setRankedRunning("start".equals(startOrStop));

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Ranked running was set to " + "start".equals(startOrStop));
		} else if (message.startsWith("!so add")) {
			String accountToAdd = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!so add".length()).toLowerCase().trim();

			Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

			if (twitchSoAccountRepository.findByAccountIdAndUsername(account.getId(), accountToAdd) == null) {
				TwitchSoAccount soAccount = new TwitchSoAccount();
				soAccount.setUsername(accountToAdd);
				twitchSoAccountRepository.save(soAccount);
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will trigger an **!so** message whenever **" + accountToAdd + "** raids or writes the first message in stream.");
		} else if (message.startsWith("!so list")) {
			String answer = "You didn't tell me who to !so yet.";

			Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

			List<TwitchSoAccount> accounts = twitchSoAccountRepository.findAllByAccountId(account.getId());

			if (accounts.size() > 0) {
				StringBuilder builder = new StringBuilder("**I send an !so command whenever one of the following accounts raids or writes their first message**:\n");
				twitchSoAccountRepository.findAll().forEach(soa -> builder.append("- ").append(soa.getUsername()).append("\n"));

				answer = builder.toString().trim();
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), answer);
		} else if (message.startsWith("!so remove")) {
			String accountToRemove = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!so remove".length()).toLowerCase().trim();

			Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

			TwitchSoAccount soAccount = twitchSoAccountRepository.findByAccountIdAndUsername(account.getId(), accountToRemove);
			if (soAccount != null) {
				twitchSoAccountRepository.delete(soAccount);
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will not trigger an **!so** message anymore whenever **" + accountToRemove + "** raids or writes the first message in stream.");
		} else if (message.startsWith("!file")) {
			String filepath = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!file".length()).trim();

//			Path path = Paths.get(Paths.get(String.format("%s\\src\\main\\resources\\html\\template-example.html", Paths.get(".").toAbsolutePath().normalize().toString())).getParent().toString(), String.format("%s/win.txt", "/../../shared/woomydx-powers"));
			Path path = Paths.get(filepath);
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("resolved path: %s", path));

			if (Files.exists(path)) {
				try (InputStream isPowerGain = new FileInputStream(path.toString())) {
					String result = new String(isPowerGain.readAllBytes(), StandardCharsets.UTF_8);

					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "File contents:");
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), result);
				} catch (IOException e) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), e.getMessage());
				}
			} else {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "This file does not exist.");
			}
		} else if (message.startsWith("!colors reset")) {
			splatoonMatchColorComponent.reset();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Color reset done.");
		} else if (message.startsWith("!reload matches")) {
			resultsExporter.forceReload();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Alright, I will force reload the last 50 matches soon.");
		} else if (message.startsWith("!reload stats")) {
			statsExporter.refreshStageAndWeaponStats();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished reloading weapon and stage stats successfully.");
		} else if (message.startsWith("!obs disconnect")) {
			obsSceneSwitcher.disconnect();
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Disconnected from obs");
		} else if (message.startsWith("!obs")) {
			String scene = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!obs".length()).trim();
			obsSceneSwitcher.switchScene(scene);
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Switched to obs scene '%s'", scene));
		} else if (message.startsWith("!twitch")) {
			String command = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!twitch".length()).trim();
			if (command.toLowerCase().startsWith("join")) {
				String user = command.substring("join".length()).trim();
				twitchBotClient.joinChannel(user);

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Joined the channel");
			} else if (command.toLowerCase().startsWith("leave")) {
				String user = command.substring("leave".length()).trim();
				twitchBotClient.leaveChannel(user);

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Left the channel");
			} else if (command.toLowerCase().startsWith("send")) {
				String content = command.substring("send".length()).trim();
				String[] contentArray = content.split(" ");

				if (contentArray.length > 1) {
					String user = contentArray[0];
					String messageToSend = content.substring(user.length()).trim();

					if (twitchBotClient.isChannelJoined(user)) {
						twitchMessageSender.send(user, messageToSend.length() > 500 ? messageToSend.substring(0, 500) : messageToSend);

						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Message sent");
					} else {
						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Bot has not joined the chat of that channel, please join it first");
					}
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Please provide a user and a message");
				}
			} else {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "you need to either use !twitch join or !twitch leave or !twitch send - wrong command, I did nothing");
			}
		} else if (message.startsWith("!users")) {
			List<Account> allUsers = accountRepository.findAll().stream()
					.sorted(Comparator.comparingLong(Account::getId))
					.collect(Collectors.toList());

			StringBuilder builder = new StringBuilder("This bot currently has **").append(allUsers.size()).append("** users");

			if (message.startsWith("!users list")) {
				builder.append("\n\nList of all users:");

				for (Account account : allUsers) {
					builder.append("\n- id: **").append(account.getId()).append("** - name: **").append(discordBot.loadUserNameFromServer(account.getDiscordId())).append("**");
				}
			}

			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), builder.toString());
		} else if (message.startsWith("!local_stats")) {
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "debug stats requested");
			if (DiscordChannelDecisionMaker.isIsLocalDebug()) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "is local - sending...");
				dailyStatsSender.sendDailyStatsToDiscord();
			}
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "debug stats done");
		} else if (message.startsWith("!daily_stats")) {
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "daily stats requested");
			if (!DiscordChannelDecisionMaker.isIsLocalDebug()) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "is server - sending...");
				dailyStatsSender.sendDailyStatsToDiscord();
			}
			discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "daily stats done");
		}
	}
}

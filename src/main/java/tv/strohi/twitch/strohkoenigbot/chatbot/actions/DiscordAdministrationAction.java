package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.StrohkoenigbotApplication;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchSoAccount;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchSoAccountRepository;
import tv.strohi.twitch.strohkoenigbot.obs.ObsController;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
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
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DiscordAdministrationAction extends ChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final StrohkoenigbotApplication strohkoenigbotApplication;

	private final TwitchAuthRepository authRepository;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final TwitchSoAccountRepository twitchSoAccountRepository;

	private final DiscordAccountLoader discordAccountLoader;
	private final TwitchMessageSender twitchMessageSender;
	private final TwitchBotClient twitchBotClient;
	private final DiscordBot discordBot;

	private final ResultsExporter resultsExporter;
	private final StatsExporter statsExporter;
	private final SplatoonMatchColorComponent splatoonMatchColorComponent;
	private final DailyStatsSender dailyStatsSender;

	private final ObsController obsController;

	private final S3Downloader s3Downloader;
	private final S3S3sRunner s3sRunner;
	private final S3RotationSender s3RotationSender;
	private final S3DailyStatsSender s3DailyStatsSender;
	private final S3NewGearChecker s3NewGearChecker;
	private final S3GameExporter s3GameExporter;

	private final ImageService imageService;

	private final S3BadgeSender badgeSender;
	private final S3EmoteSender emoteSender;
	private final S3NameplateSender nameplateSender;

	private final S3XLeaderboardDownloader leaderboardDownloader;

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null || !Long.toString(DiscordBot.ADMIN_ID).equals(args.getUserId())) {
			return;
		}

		try {
			message = message.toLowerCase().trim();

			if (message.startsWith("!start")) {
				Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(null);

				if (account != null && !twitchBotClient.isLive(account.getTwitchUserId())) {
					twitchBotClient.setFakeDebug(true);
					resultsExporter.start(account);
				}

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Results export started successfully.");
			} else if (message.startsWith("!stop")) {
				Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(null);

				if (account != null && twitchBotClient.isLive(account.getTwitchUserId())) {
					twitchBotClient.setFakeDebug(false);
					resultsExporter.stop(account);
				}

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Results export stopped successfully.");
			} else if (message.startsWith("!config set") && message.contains("=")) {
				String commandToSet = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config set".length()).trim();

				String propertyName = commandToSet.split("=")[0];
				String propertyValue = commandToSet.substring(propertyName.length() + 1).trim();

				Configuration config = new Configuration();
				config.setConfigName(propertyName);
				List<Configuration> configs = configurationRepository.findAllByConfigName(propertyName);
				if (!configs.isEmpty()) {
					config = configs.get(0);
				}

				config.setConfigValue(propertyValue);

				config = configurationRepository.save(config);

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d was stored into database.", config.getId()));
			} else if (message.startsWith("!config add") && message.contains("=")) {
				String commandToSet = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config add".length()).trim();

				String propertyName = commandToSet.split("=")[0];
				String propertyValue = commandToSet.substring(propertyName.length() + 1).trim();

				Configuration config = new Configuration();
				config.setConfigName(propertyName);
				config.setConfigValue(propertyValue);

				config = configurationRepository.save(config);

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d was added into database.", config.getId()));
			} else if (message.startsWith("!config get") && !message.toLowerCase().contains("pass")) {
				String propertyName = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config get".length()).trim();

				Configuration config = null;
				List<Configuration> configs = configurationRepository.findAllByConfigName(propertyName);
				if (!configs.isEmpty()) {
					config = configs.get(0);
				}

				if (config != null) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Configuration %d: `%s`", config.getId(), config.getConfigValue()));
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Such a configuration does not exist.");
				}
			} else if (message.startsWith("!config remove")) {
				String propertyName = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!config remove".length()).trim();

				List<Configuration> configs = configurationRepository.findAllByConfigName(propertyName);
				if (!configs.isEmpty()) {
					configurationRepository.deleteAll(configs);
				}

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Configurations were removed from database.");
			} else if (message.startsWith("!platform")) {
				String streamPrefix = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim()
					.substring("!platform".length()).toLowerCase().trim();

				Configuration config = new Configuration();
				config.setConfigName(ConfigurationRepository.streamPrefix);
				List<Configuration> configs = configurationRepository.findAllByConfigName(ConfigurationRepository.streamPrefix);
				if (!configs.isEmpty()) {
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

				var foundNullAccount = twitchSoAccountRepository.findByAccountIdAndUsername(account.getId(), accountToAdd);
				if (foundNullAccount != null) {
					foundNullAccount.setAccountId(account.getId());
					twitchSoAccountRepository.save(foundNullAccount);
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I fixed the database entry and will trigger an **!so** message whenever **" + accountToAdd + "** writes the first message in stream.");
				} else if (twitchSoAccountRepository.findByAccountIdAndUsername(account.getId(), accountToAdd) == null) {
					TwitchSoAccount soAccount = new TwitchSoAccount();
					soAccount.setAccountId(account.getId());
					soAccount.setUsername(accountToAdd);
					twitchSoAccountRepository.save(soAccount);
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will trigger an **!so** message whenever **" + accountToAdd + "** writes the first message in stream.");
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "A shoutout trigger for account **" + accountToAdd + "** does already exist.");
				}

			} else if (message.startsWith("!so list")) {
				String answer = "You didn't tell me who to !so yet.";

				Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

				List<TwitchSoAccount> accounts = twitchSoAccountRepository.findAllByAccountId(account.getId());

				if (!accounts.isEmpty()) {
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
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will not trigger an **!so** message anymore whenever **" + accountToRemove + "** writes the first message in stream.");
				} else if ((soAccount = twitchSoAccountRepository.findByAccountIdAndUsername(null, accountToRemove)) != null) {
					twitchSoAccountRepository.delete(soAccount);
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "I will not trigger an **!so** message anymore whenever **" + accountToRemove + "** writes the first message in stream.");
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "A shoutout trigger for account **" + accountToRemove + "** does not exist.");
				}
			} else if (message.startsWith("!so fix")) {
				Account account = discordAccountLoader.loadAccount(Long.parseLong(args.getUserId()));

				var updatedUsernames = new ArrayList<String>();
				var deletedUsernames = new ArrayList<String>();

				var allSoAccounts = twitchSoAccountRepository.findAll().stream()
					.filter(soAcc -> soAcc.getAccountId() == null)
					.collect(Collectors.toCollection(LinkedList::new));

				while (!allSoAccounts.isEmpty()) {
					var element = allSoAccounts.poll();

					if (twitchSoAccountRepository.findByAccountIdAndUsername(account.getId(), element.getUsername()) != null) {
						twitchSoAccountRepository.delete(element);
						deletedUsernames.add(element.getUsername());
					} else {
						element.setAccountId(account.getId());
						twitchSoAccountRepository.save(element);
						updatedUsernames.add(element.getUsername());
					}
				}

				var builder = new StringBuilder("**Update result for request of setting all null account ids to __").append(account.getId()).append("__**\n\n__Fixed__");
				updatedUsernames.forEach(un -> builder.append("\n- ").append(un));
				builder.append("\n\n__Deleted (duplicate)__");
				deletedUsernames.forEach(un -> builder.append("\n- ").append(un));

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), builder.toString());
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
			} else if (message.startsWith("!obs enable")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Enabling Obs Controller...");
				obsController.setObsEnabled(true);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Obs Controller is now enabled");
			} else if (message.startsWith("!obs disable")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Disabling Obs Controller...");
				obsController.setObsEnabled(false);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Obs Controller is now disabled");
			} else if (message.startsWith("!obs reset counter")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Resetting Obs Controller fail counter...");
				obsController.resetFailedCounter();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Obs Controller fail counter is now reset");
			} else if (message.startsWith("!obs scene")) {
				String scene = ((String) args.getArguments().getOrDefault(ArgumentKey.Message, null)).trim().substring("!obs scene".length()).trim();
				obsController.switchScene(scene, result ->
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Switch to obs scene '%s' successful: %b", scene, result)));
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
				if (DiscordChannelDecisionMaker.isLocalDebug()) {
					Long accountId;
					String messageWithoutCommand = message.trim().substring("!local_stats".length()).trim();
					if (!messageWithoutCommand.isEmpty() && (accountId = tryParseId(messageWithoutCommand)) != null) {
						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("is local - sending for account Id %d...", accountId));
						dailyStatsSender.sendDailyStatsToAccount(accountId);
					} else {
						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "is local - sending - sending...");
						dailyStatsSender.sendDailyStatsToDiscord();
					}
				}
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "debug stats done");
			} else if (message.startsWith("!daily_stats")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "daily stats requested");
				if (!DiscordChannelDecisionMaker.isLocalDebug()) {
					Long accountId;
					String messageWithoutCommand = message.trim().substring("!daily_stats".length()).trim();
					if (!messageWithoutCommand.isEmpty() && (accountId = tryParseId(messageWithoutCommand)) != null) {
						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("is server - sending for account Id %d...", accountId));
						dailyStatsSender.sendDailyStatsToAccount(accountId);
					} else {
						discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "is server - sending...");
						dailyStatsSender.sendDailyStatsToDiscord();
					}
				}
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "daily stats done");
			} else if (message.startsWith("!cookie retrieve")) {
				Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(null);

				if (account != null) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Cookie: `%s`", account.getSplatoonCookie()));
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "No cookie for you tsk tsk tsk");
				}
			} else if (message.startsWith("!gtoken retrieve")) {
				Account account = accountRepository.findAll().stream()
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.findFirst()
					.orElse(null);

				if (account != null) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("GToken: ||`%s`||", account.getGTokenSplatoon3()));
				} else {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "No gToken for you tsk tsk tsk");
				}
			} else if (message.startsWith("!reimport s3")) {
				s3Downloader.downloadBattles(true);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished reimport");
			} else if (message.startsWith("!run s3s")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Running s3s manually");
				s3sRunner.runS3S();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished s3s run");
			} else if (message.startsWith("!activate db s3")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Activating Splatoon 3 database...");

				s3Downloader.setPauseDownloader(true);
				s3RotationSender.setPauseSender(true);
				imageService.setPauseService(true);

				var useNewWay = configurationRepository.findByConfigName("s3UseDatabase").stream()
					.findFirst()
					.orElse(Configuration.builder()
						.configName("s3UseDatabase")
						.build());

				useNewWay.setConfigValue("true");

				configurationRepository.save(useNewWay);

				s3Downloader.downloadBattles(true);

				s3Downloader.setPauseDownloader(false);
				s3RotationSender.setPauseSender(false);
				imageService.setPauseService(false);

				s3RotationSender.refreshRotations();

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Splatoon 3 database has been enabled successfully.");
			} else if (message.startsWith("!tryparse")) {
				String uuid = message.substring("!tryparse".length()).trim();

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Attempting to parse for uuid '%s'", uuid));
				s3Downloader.tryParseAllBattles(uuid);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished parse");
			} else if (message.startsWith("!repost rotations s3")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Forcing rotation posts");
				s3RotationSender.refreshRotations(true);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished posting forced rotation posts");
			} else if (message.startsWith("!reload gear s3")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Forcing new gear reload");
				s3NewGearChecker.checkForNewGearInSplatNetShop(false);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished reloading new gear");
			} else if (message.startsWith("!repost stats s3")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Forcing daily stats messages");
				s3DailyStatsSender.sendStats(true);
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished posting daily stats messages");
			} else if (message.startsWith("!restart bot")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Issuing restart");
				StrohkoenigbotApplication.restart();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Issued restart");
			} else if (message.startsWith("!shutdown bot")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Issuing shutdown");
				strohkoenigbotApplication.shutdown();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Issued shutdown");
			} else if (message.startsWith("!force twitch live")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Forcing twitch going live state");
				var account = accountRepository.findAll().stream()
					.filter(a -> a.getTwitchUserId() != null && !a.getTwitchUserId().isBlank())
					.findFirst()
					.orElse(null);

				if (account != null) {
					twitchBotClient.goLive(account.getTwitchUserId());
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Successfully forced twitch going live state for twitch channel id %s", account.getTwitchUserId()));
				} else {
					twitchBotClient.forceLive();
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Successfully forced twitch going live state without twitch user id");
				}
			} else if (message.startsWith("!force twitch offline")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Forcing twitch going offline state");

				var account = accountRepository.findAll().stream()
					.filter(a -> a.getTwitchUserId() != null && !a.getTwitchUserId().isBlank())
					.findFirst()
					.orElse(null);

				if (account != null) {
					twitchBotClient.goOffline(account.getTwitchUserId());
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), String.format("Successfully forced twitch going offline state for twitch channel id %s", account.getTwitchUserId()));
				} else {
					twitchBotClient.forceOffline();
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Successfully forced twitch going offline state without twitch user id");
				}
			} else if (message.startsWith("!repost badges")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Reposting badges...");
				badgeSender.reloadBadges();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished badge repost.");
			} else if (message.startsWith("!repost emotes")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Reposting emotes...");
				emoteSender.reloadEmotes();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished emote repost.");
			} else if (message.startsWith("!repost nameplates")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Reposting nameplates...");
				nameplateSender.repostNameplates();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished nameplate repost.");
			} else if (message.startsWith("!load top500")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Loading top500 powers...");

				var powers = leaderboardDownloader.loadTop500MinPower();
				var builder = new StringBuilder("current top 500 powers:");
				for (var key : powers.keySet()) {
					builder.append("\n- ").append(findModeName(key)).append(": ").append(powers.get(key));
				}
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), builder.toString());

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished loading top500 powers.");
			} else if (message.startsWith("!fix db")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Fixing database doubled entries...");
				s3Downloader.fixBrokenDatabaseEntries();
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished fixing database doubled entries.");
			} else if (message.startsWith("!export games")) {
				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Exporting missed s3s entries to file...");
				try {
					s3Downloader.downloadBattles(true);

					var split = message.split(" +");
					var top = Integer.parseInt(split[2]);
					var skip = Integer.parseInt(split[3]);

					s3GameExporter.exportGames(DiscordBot.ADMIN_ID, top, skip);
				} catch (Exception ex) {
					discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Exception while exporting Games.");
					exceptionLogger.logException(logger, ex);
				}

				discordBot.sendPrivateMessage(Long.parseLong(args.getUserId()), "Finished exporting missed s3s entries to file.");
			}
		} catch (Exception e) {
			logSender.sendLogs(logger, "An error occured during admin command execution\nSee logs for details!");
			exceptionLogger.logException(logger, e);
		}
	}

	private String findModeName(String key) {
		if (key.toLowerCase(Locale.ROOT).contains("ar")) {
			return "Zones";
		} else if (key.toLowerCase(Locale.ROOT).contains("lf")) {
			return "Tower";
		} else if (key.toLowerCase(Locale.ROOT).contains("gl")) {
			return "Rainmaker";
		} else {
			return "Clams";
		}
	}

	private Long tryParseId(String message) {
		try {
			return Long.parseLong(message);
		} catch (Exception ex) {
			return null;
		}
	}
}

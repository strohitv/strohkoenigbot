package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.WeaponRequestRankingAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResultsCollection;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.ExceptionSender;
import tv.strohi.twitch.strohkoenigbot.utils.SplatoonMatchColorComponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final AccountRepository accountRepository;
	private final Splatoon2MatchRepository matchRepository;

	private final MatchFiller matchFiller;
	private final MatchReloader matchReloader;
	private final WeaponStatsFiller weaponStatsFiller;
	private final AbilityMatchFiller abilityMatchFiller;
	private final ClipRefresher clipRefresher;
	private final ObsController obsController;
	private final ExceptionSender exceptionSender;

	@Autowired
	public ResultsExporter(AccountRepository accountRepository, Splatoon2MatchRepository matchRepository, MatchFiller matchFiller, MatchReloader matchReloader, WeaponStatsFiller weaponStatsFiller, AbilityMatchFiller abilityMatchFiller, ClipRefresher clipRefresher, ObsController obsController, ExceptionSender exceptionSender) {
		this.accountRepository = accountRepository;
		this.matchRepository = matchRepository;
		this.matchFiller = matchFiller;
		this.matchReloader = matchReloader;
		this.weaponStatsFiller = weaponStatsFiller;
		this.abilityMatchFiller = abilityMatchFiller;
		this.clipRefresher = clipRefresher;
		this.obsController = obsController;
		this.exceptionSender = exceptionSender;

		TwitchBotClient.setResultsExporter(this);
	}

	private boolean loadSilently = false;

	private boolean isRankedRunning = false;

	public void setRankedRunning(boolean rankedRunning) {
		isRankedRunning = rankedRunning;
	}

	private boolean forceReload = false;

	public void forceReload() {
		forceReload = true;
	}

	private RequestSender splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(RequestSender splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private TwitchMessageSender twitchMessageSender;

	@Autowired
	public void setTwitchMessageSender(TwitchMessageSender twitchMessageSender) {
		this.twitchMessageSender = twitchMessageSender;
	}

	private Statistics statistics;

	@Autowired
	public void setStatistics(Statistics statistics) {
		this.statistics = statistics;
	}

	private SplatoonMatchColorComponent splatoonMatchColorComponent;

	@Autowired
	public void setSplatoonMatchColorComponent(SplatoonMatchColorComponent splatoonMatchColorComponent) {
		this.splatoonMatchColorComponent = splatoonMatchColorComponent;
	}

	private ExtendedStatisticsExporter extendedStatisticsExporter;

	@Autowired
	public void setExtendedStatisticsExporter(ExtendedStatisticsExporter extendedStatisticsExporter) {
		this.extendedStatisticsExporter = extendedStatisticsExporter;
	}

	private WeaponRequestRankingAction weaponRequestRankingAction;

	@Autowired
	public void setWeaponRequestRankingAction(WeaponRequestRankingAction weaponRequestRankingAction) {
		this.weaponRequestRankingAction = weaponRequestRankingAction;
	}

	private StatsExporter statsExporter;

	@Autowired
	public void setStatsExporter(StatsExporter statsExporter) {
		this.statsExporter = statsExporter;
	}

	private TwitchBotClient twitchBotClient;

	@Autowired
	public void setTwitchBotClient(TwitchBotClient twitchBotClient) {
		this.twitchBotClient = twitchBotClient;
	}

	public void start(Account account) {
		discordBot.sendPrivateMessage(account.getDiscordId(), "starting the stream!");
		statistics.reset();
		extendedStatisticsExporter.start(Instant.now(), account.getId());
	}

	public void stop(Account account) {
		discordBot.sendPrivateMessage(account.getDiscordId(), "stopping the stream!");
		stop();
	}

	public void stop() {
		isRankedRunning = false;
		statistics.stop();
		extendedStatisticsExporter.end();

		weaponRequestRankingAction.stop();

		obsController.disconnect();
	}

	private int rateLimitNumber = 20;

	// TODO change it so that waiting is determined by the type of account (main: true = every 5 minutes, main: false = every 60 minutes)
	private final int attemptsPerMinute = 3;
	private final int refreshEveryXMinutesMain = 10;
	private final int refreshEveryXMinutesOther = 60;

//	@Scheduled(cron = "*/10 * * * * *")
//	@Scheduled(fixedDelay = 10000, initialDelay = 90000)
	@Scheduled(fixedDelay = 20000)
	public void loadGameResultsScheduled() {
		logger.debug("running results exporter");

		List<Account> accounts = accountRepository.findAll().stream()
				.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank())
				.filter(da -> da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
				// TODO rework to make it work with any other account
				.filter(Account::getIsMainAccount)
				.collect(Collectors.toList());

		for (Account account : accounts) {
			// TODO make isStreamRunning and rateLimitNumber dependent from account
			boolean isStreamRunning = account.getIsMainAccount() && twitchBotClient.isLive(account.getTwitchUserId());

			if (isStreamRunning || rateLimitNumber == 0) {
				logger.info("loading results");

				try {
					SplatNetMatchResultsCollection splatNetMatches = splatoonResultsLoader.querySplatoonApiForAccount(account, "/api/results", SplatNetMatchResultsCollection.class);

					if (splatNetMatches != null) {
						List<SplatNetMatchResult> results = new ArrayList<>();
						for (int i = splatNetMatches.getResults().length - 1; i >= 0; i--) {
							results.add(splatNetMatches.getResults()[i]);
						}

						if (forceReload) {
							matchReloader.forceMatchReload(account, results);
							forceReload = false;
							loadSilently = true;
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), "removed last 50 matches successfully");
						}

						int maxSavedBattleNumber = matchRepository.findMaxBattleNumber(account.getId());
						results = results.stream()
								.filter(r -> r.getBattleNumberAsInteger() > maxSavedBattleNumber) // matchRepository.findBySplatnetBattleNumber(r.getBattleNumberAsInteger()) == null)
								.collect(Collectors.toList());

						if (results.size() > 0) {
							splatoonMatchColorComponent.reset();
						}

						for (SplatNetMatchResult singleResult : results) {
							Splatoon2Match match = matchFiller.fill(account, singleResult);
							match = matchRepository.save(match);

							weaponRequestRankingAction.addMatch(match);

							weaponStatsFiller.fill(match, singleResult, match.getWeaponId(), account.getId());
							abilityMatchFiller.fill(match, singleResult);

							if (!loadSilently) {
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), MatchMessageFormatter.getAddedMatchMessage(match));
								twitchMessageSender.send("strohkoenig", MatchMessageFormatter.getMatchResultPerformance(match));
//								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), String.format("Added used abilities to Match with id **%d**", match.getId()));
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), MatchMessageFormatter.getMatchResultMessage(match, singleResult));
							}

							clipRefresher.refresh(account.getId(), match, loadSilently);
						}

						if (loadSilently) {
							statsExporter.refreshStageAndWeaponStats();
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), "reload of all 50 matches completed successfully");
							loadSilently = false;
						}

						if (isStreamRunning) {
							statistics.addMatches(results);
							statistics.exportHtml();
							extendedStatisticsExporter.export(account.getId(), results);
						}
					}

					if (isStreamRunning && isRankedRunning) {
						obsController.controlOBS(account, extendedStatisticsExporter.getStarted().getEpochSecond());
					}
				} catch (Exception ex) {
					logger.error(ex);
					exceptionSender.send(ex);
				}
			}
		}

		rateLimitNumber = (rateLimitNumber + 1) % (refreshEveryXMinutesMain * attemptsPerMinute);
	}
}

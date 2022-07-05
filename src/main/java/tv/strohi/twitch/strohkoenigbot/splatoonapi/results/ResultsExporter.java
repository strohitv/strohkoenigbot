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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
		if (account != null) {
			discordBot.sendPrivateMessage(account.getDiscordId(), "starting the stream!");
			statistics.reset();
			extendedStatisticsExporter.start(Instant.now(), account.getId());
		}
	}

	public void stop(Account account) {
		if (account != null) {
			discordBot.sendPrivateMessage(account.getDiscordId(), "stopping the stream!");
			stop();
		}
	}

	public void stop() {
		isRankedRunning = false;
		statistics.stop();
		extendedStatisticsExporter.end();

		weaponRequestRankingAction.stop();

		obsController.disconnect();
	}

	private final List<Account> blockedAccounts = new ArrayList<>();

	//	@Scheduled(cron = "*/10 * * * * *")
	//	@Scheduled(fixedDelay = 10000, initialDelay = 90000)
	@Scheduled(fixedDelay = 20000)
	public void loadGameResultsScheduled() {
		logger.debug("running results exporter");

		List<Account> accounts = accountRepository.findAll().stream()
				.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank())
				.filter(da -> da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
				.filter(a -> a.getSplatoonCookie() != null && !a.getSplatoonCookie().isBlank())
				.collect(Collectors.toList());

		for (Account account : accounts) {
			boolean isMidnight = isDirectlyPastMidnight(account.getTimezone()) && blockedAccounts.stream().noneMatch(a -> a.getId() == account.getId());
			boolean forceRefresh = isMidnight || (account.getIsMainAccount() && twitchBotClient.isLive(account.getTwitchUserId()));

			if (account.getRateLimitNumber() == null) {
				account.setRateLimitNumber(new Random().nextInt(30));
			}

			if (forceRefresh || account.getRateLimitNumber() == 0) {
				logger.info("loading results");

				if (isMidnight) {
					blockedAccounts.add(account);
				} else if (!isDirectlyPastMidnight(account.getTimezone()) && blockedAccounts.stream().anyMatch(a -> a.getId() == account.getId())) {
					blockedAccounts.removeIf(a -> a.getId() == account.getId());
				}

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

						if (forceRefresh) {
							statistics.addMatches(results);
							statistics.exportHtml();
							extendedStatisticsExporter.export(account.getId(), results);
						}
					}

					if (forceRefresh && isRankedRunning) {
						obsController.controlOBS(account, extendedStatisticsExporter.getStarted().getEpochSecond());
					}
				} catch (Exception ex) {
					logger.error(ex);
					exceptionSender.send(ex);
				}
			}

			refreshRateLimitNumber(account);
		}
	}

	private void refreshRateLimitNumber(Account account) {
		int attemptsPerMinute = 3;
		int refreshEveryXMinutesMain = 10;
		int refreshEveryXMinutesOther = 60;

		int newRateLimitNumber = (account.getRateLimitNumber() + 1) % ((account.getIsMainAccount() ? refreshEveryXMinutesMain : refreshEveryXMinutesOther) * attemptsPerMinute);
		account.setRateLimitNumber(newRateLimitNumber);
		accountRepository.save(account);
	}

	private boolean isDirectlyPastMidnight(String timezone) {
		if (timezone != null && !timezone.isBlank()) {
			ZonedDateTime time = Instant.now().atZone(ZoneId.of(timezone));
			return time.getHour() == 0 && time.getMinute() >= 7 && time.getMinute() <= 8;
		}

		return false;
	}
}

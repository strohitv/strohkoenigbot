package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.WeaponRequestRankingAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.obs.ObsSceneSwitcher;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;
import tv.strohi.twitch.strohkoenigbot.utils.SplatoonMatchColorComponent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private boolean alreadyRunning = false;
	private boolean isStreamRunning = false;

	@Autowired
	public ResultsExporter() {
		TwitchBotClient.setResultsExporter(this);
	}

	private boolean isRankedRunning = false;

	public void setRankedRunning(boolean rankedRunning) {
		isRankedRunning = rankedRunning;
	}

	private RequestSender splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(RequestSender splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2MatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(Splatoon2MatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private Splatoon2RotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(Splatoon2RotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private Splatoon2AbilityMatchRepository abilityMatchRepository;

	@Autowired
	public void setAbilityMatchRepository(Splatoon2AbilityMatchRepository abilityMatchRepository) {
		this.abilityMatchRepository = abilityMatchRepository;
	}

	private Splatoon2MonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(Splatoon2MonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private Splatoon2ClipRepository clipRepository;

	@Autowired
	public void setClipRepository(Splatoon2ClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
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

	private StagesExporter stagesExporter;

	@Autowired
	public void setStagesExporter(StagesExporter stagesExporter) {
		this.stagesExporter = stagesExporter;
	}

	private WeaponExporter weaponExporter;

	@Autowired
	public void setWeaponExporter(WeaponExporter weaponExporter) {
		this.weaponExporter = weaponExporter;
	}

	private GearExporter gearExporter;

	@Autowired
	public void setGearExporter(GearExporter gearExporter) {
		this.gearExporter = gearExporter;
	}

	private AbilityExporter abilityExporter;

	@Autowired
	public void setAbilityExporter(AbilityExporter abilityExporter) {
		this.abilityExporter = abilityExporter;
	}

	private PeaksExporter peaksExporter;

	@Autowired
	public void setPeaksExporter(PeaksExporter peaksExporter) {
		this.peaksExporter = peaksExporter;
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

	private ObsSceneSwitcher obsSceneSwitcher;

	@Autowired
	public void setObsSceneSwitcher(ObsSceneSwitcher obsSceneSwitcher) {
		this.obsSceneSwitcher = obsSceneSwitcher;
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

	private boolean forceReload = false;
	private boolean loadSilently = false;

	public void forceReload() {
		forceReload = true;
	}

	public String getHtml() {
		return statistics.getCurrentHtml();
	}

	public void start(long accountId) {
		discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "starting the stream!");

		isStreamRunning = true;
		statistics.reset();

		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		Splatoon2MonthlyResult result = monthlyResultRepository.findByAccountIdAndPeriodYearAndPeriodMonth(accountId, year, month);
		Map<Splatoon2Rule, Double> startPowers = new HashMap<>() {{
			put(Splatoon2Rule.SplatZones, result.getZonesCurrent());
			put(Splatoon2Rule.Rainmaker, result.getRainmakerCurrent());
			put(Splatoon2Rule.TowerControl, result.getTowerCurrent());
			put(Splatoon2Rule.ClamBlitz, result.getClamsCurrent());
		}};
		extendedStatisticsExporter.start(Instant.now(), startPowers);
	}

	public void stop() {
		discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "stopping the stream!");

		isStreamRunning = false;
		isRankedRunning = false;
		statistics.stop();
		extendedStatisticsExporter.end();

		weaponRequestRankingAction.stop();

		obsSceneSwitcher.disconnect();
	}

	public boolean isStreamRunning() {
		return isStreamRunning;
	}

	//	@Scheduled(cron = "*/10 * * * * *")
	@Scheduled(fixedDelay = 10000, initialDelay = 90000)
	public void loadGameResultsScheduled() {
		logger.debug("running results exporter");
		if (!alreadyRunning) {
			alreadyRunning = true;
			logger.info("loading results");

			List<Account> accounts = accountRepository.findAll().stream()
					.filter(da -> da.getSplatoonCookie() != null && !da.getSplatoonCookie().isBlank())
					.filter(da -> da.getSplatoonCookieExpiresAt() != null && Instant.now().isBefore(da.getSplatoonCookieExpiresAt()))
					// TODO rework to make it work with any other account
					.filter(Account::getIsMainAccount)
					.collect(Collectors.toList());

			for (Account account : accounts) {
				try {
					SplatNetMatchResultsCollection collection = splatoonResultsLoader.querySplatoonApiForAccount(account, "/api/results", SplatNetMatchResultsCollection.class);

					if (collection != null) {
						List<SplatNetMatchResult> results = new ArrayList<>();
						for (int i = collection.getResults().length - 1; i >= 0; i--) {
							results.add(collection.getResults()[i]);
						}

						if (forceReload) {
							for (SplatNetMatchResult singleResult : results) {
								Splatoon2Match match = matchRepository.findByAccountIdAndBattleNumber(account.getId(), singleResult.getBattle_number());

								if (match != null) {
									long id = match.getId();

									clipRepository.getAllByMatchId(id).forEach(clip -> {
										clip.setMatchId(null);
										clipRepository.save(clip);
									});

									Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(match.getWeaponId(), account.getId()).orElse(null);
									if (weaponStats != null) {
										if (match.getMatchResult() == Splatoon2MatchResult.Win) {
											weaponStats.setWins(weaponStats.getWins() - 1);
										} else {
											weaponStats.setDefeats(weaponStats.getDefeats() - 1);
										}

										weaponStatsRepository.save(weaponStats);
									}

									abilityMatchRepository.deleteAll(abilityMatchRepository.findAllByMatchId(id));
									matchRepository.delete(match);
								}
							}

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
							Splatoon2Match match = new Splatoon2Match();
							match.setAccountId(account.getId());

							match.setBattleNumber(singleResult.getBattle_number());
							match.setSplatnetBattleNumber(singleResult.getBattleNumberAsInteger());

							match.setStartTime(singleResult.getStart_time());
							match.setElapsedTime(singleResult.getElapsed_time());
							match.setEndTime(singleResult.getStart_time() + singleResult.getElapsed_time());

							match.setStageId(stagesExporter.loadStage(singleResult.getStage()).getId());
							match.setMode(Splatoon2Mode.getModeByName(singleResult.getGame_mode().getKey()));
							match.setRule(Splatoon2Rule.getRuleByName(singleResult.getRule().getKey()));

							Splatoon2Rotation rotation
									= rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(match.getStartTime(), match.getEndTime(), match.getMode());

							if (rotation != null
									&& (Objects.equals(rotation.getStageAId(), match.getStageId()) || Objects.equals(rotation.getStageBId(), match.getStageId()))) {
								match.setRotationId(rotation.getId());
							}

							if (singleResult.getUdemae() != null) {
								match.setRank(singleResult.getUdemae().getName());
							}

							match.setXPower(singleResult.getX_power());
							match.setXPowerEstimate(singleResult.getEstimate_gachi_power());
							match.setXLobbyPower(singleResult.getEstimate_x_power());

							match.setLeagueTag(singleResult.getTag_id());
							match.setLeaguePower(singleResult.getLeague_point());
							match.setLeaguePowerMax(singleResult.getMax_league_point());
							match.setLeaguePowerEstimate(singleResult.getMy_estimate_league_point());
							match.setLeagueEnemyPower(singleResult.getOther_estimate_league_point());

							Splatoon2Weapon weapon = weaponExporter.loadWeapon(singleResult.getPlayer_result().getPlayer().getWeapon());

							match.setWeaponId(weapon.getId());
							match.setTurfGain(singleResult.getPlayer_result().getGame_paint_point());
							match.setTurfTotal(singleResult.getWeapon_paint_point());

							match.setKills(singleResult.getPlayer_result().getKill_count());
							match.setAssists(singleResult.getPlayer_result().getAssist_count());
							match.setDeaths(singleResult.getPlayer_result().getDeath_count());
							match.setSpecials(singleResult.getPlayer_result().getSpecial_count());

							match.setOwnScore(singleResult.getMy_team_count());
							match.setEnemyScore(singleResult.getOther_team_count());

							match.setOwnPercentage(singleResult.getMy_team_percentage());
							match.setEnemyPercentage(singleResult.getOther_team_percentage());

							match.setMatchResult(Splatoon2MatchResult.parseResult(singleResult.getMy_team_result().getKey()));
							match.setIsKo(singleResult.getMy_team_count() != null && singleResult.getOther_team_count() != null
									&& (singleResult.getMy_team_count() == 100 || singleResult.getOther_team_count() == 100));

							match.setHeadgearId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getHead()).getId());
							match.setClothesId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getClothes()).getId());
							match.setShoesId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getShoes()).getId());

							// only set for turf war matches
							match.setCurrentFlag(singleResult.getWin_meter());

							match.setMatchResultOverview(singleResult);
							match.setMatchResultDetails(null);

							if (account.getIsMainAccount()) {
								// difference: gear and results of other players are included
								SplatNetMatchResult loadedMatch
										= splatoonResultsLoader.querySplatoonApiForAccount(account, String.format("/api/results/%s", singleResult.getBattle_number()), SplatNetMatchResult.class);
								match.setMatchResultDetails(loadedMatch);
							}

							matchRepository.save(match);

							weaponRequestRankingAction.addMatch(match);

							Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(weapon.getId(), account.getId()).orElse(null);
							if (weaponStats == null) {
								weaponStats = new Splatoon2WeaponStats();
								weaponStats.setWeaponId(weapon.getId());
								weaponStats.setAccountId(account.getId());
								weaponStats.setTurf(0L);
								weaponStats.setWins(0);
								weaponStats.setDefeats(0);
								weaponStats.setCurrentFlag(0.0);
								weaponStats.setMaxFlag(0.0);
							}

							if (singleResult.getWin_meter() != null) {
								weaponStats.setCurrentFlag(singleResult.getWin_meter());

								if (weaponStats.getMaxFlag() == null || weaponStats.getMaxFlag() < singleResult.getWin_meter()) {
									weaponStats.setMaxFlag(singleResult.getWin_meter());
								}
							}

							weaponStats.setTurf(singleResult.getWeapon_paint_point());
							if (match.getMatchResult() == Splatoon2MatchResult.Win) {
								weaponStats.setWins(weaponStats.getWins() + 1);
							} else {
								weaponStats.setDefeats(weaponStats.getDefeats() + 1);
							}

							weaponStatsRepository.save(weaponStats);

							if (!loadSilently) {
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Put new Match with id **%d** for mode **%s** and rule **%s** into database. It was a **%s**.",
												match.getId(),
												match.getMode(),
												match.getRule(),
												match.getMatchResult()));

								twitchMessageSender.send("strohkoenig",
										String.format("Last match: %s (%s : %s %s) - own stats: %dp ink, %d kills, %d assists, %d specials, %d deaths",
												match.getRule().getAsString(),
												match.getOwnScore() != null ? String.format("%d", match.getOwnScore()) : String.format("%.1f%%", match.getOwnPercentage()),
												match.getEnemyScore() != null ? String.format("%d", match.getEnemyScore()) : String.format("%.1f%%", match.getEnemyPercentage()),
												match.getMatchResult() == Splatoon2MatchResult.Win ? "win" : "defeat",
												match.getTurfGain(),
												match.getKills(),
												match.getAssists(),
												match.getSpecials(),
												match.getDeaths()));
							}

							List<Splatoon2AbilityMatch> abilitiesUsedInMatch = new ArrayList<>();

							abilitiesUsedInMatch.addAll(parseAbilities(
									singleResult.getPlayer_result().getPlayer().getHead_skills(),
									singleResult.getPlayer_result().getPlayer().getHead().getKind(),
									match.getId()));
							abilitiesUsedInMatch.addAll(parseAbilities(
									singleResult.getPlayer_result().getPlayer().getClothes_skills(),
									singleResult.getPlayer_result().getPlayer().getClothes().getKind(),
									match.getId()));
							abilitiesUsedInMatch.addAll(parseAbilities(
									singleResult.getPlayer_result().getPlayer().getShoes_skills(),
									singleResult.getPlayer_result().getPlayer().getShoes().getKind(),
									match.getId()));

							abilityMatchRepository.saveAll(abilitiesUsedInMatch);

							if (!loadSilently) {
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), String.format("Added used abilities to Match with id **%d**", match.getId()));
							}

							if (!loadSilently) {
								String discordResultMessage = String.format(
										"**I finished a Splatoon 2 match!**\n" +
												"\n" +
												"**General results**:\n" +
												"- Mode: **%s**\n" +
												"- Rule: **%s**\n" +
												"- It was a **%s**\n" +
												"- My weapon: **%s**\n" +
												"- Our score: **%s**\n" +
												"- Enemy score: **%s**\n" +
												"\n" +
												"**Personal results**:\n" +
												"- Splats: **%d**\n" +
												"- Assists: **%d**\n" +
												"- Deaths: **%d**\n" +
												"- Paint: + **%d** points\n\n" +
												"---------------------------------",
										match.getMode(),
										match.getRule(),
										match.getMatchResult(),
										singleResult.getPlayer_result().getPlayer().getWeapon().getName(),
										match.getOwnPercentage() != null ? match.getOwnPercentage() : match.getOwnScore(),
										match.getEnemyPercentage() != null ? match.getEnemyPercentage() : match.getEnemyScore(),

										match.getKills(),
										match.getAssists(),
										match.getDeaths(),
										match.getTurfGain());

								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), discordResultMessage);
							}

							// refresh clips and send them to discord
							List<Splatoon2Clip> clips = clipRepository.getAllByAccountIdAndStartTimeIsGreaterThanAndEndTimeIsLessThan(account.getId(), match.getStartTime(), match.getEndTime());
							if (clips.size() > 0) {
								StringBuilder ratingsMessageBuilder = new StringBuilder("**Viewers rated my performance**:\n");

								for (Splatoon2Clip clip : clips) {
									ratingsMessageBuilder.append(String.format("\n- **%s** play - Clip: <%s> - Description: \"%s\"",
											clip.getIsGoodPlay() ? "GOOD" : "BAD",
											clip.getClipUrl(),
											clip.getDescription()));

									clip.setMatchId(match.getId());
								}

								if (!loadSilently) {
									discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), ratingsMessageBuilder.toString());
								}

								clipRepository.saveAll(clips);
							}
						}

						if (loadSilently) {
							statsExporter.refreshStageAndWeaponStats();
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), "reload of all 50 matches completed successfully");
							loadSilently = false;
						}

						if (isStreamRunning) {
							statistics.addMatches(results);
							statistics.exportHtml();
						}

						refreshMonthlyRankedResults(account.getId(), results);

						if (isStreamRunning) {
							extendedStatisticsExporter.export(account.getId());
						}
					}

					if (isStreamRunning && isRankedRunning) {
						controlOBS(account);
					}
				} catch (Exception ex) {
					logger.error(ex);

					try {
						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Exception occured while refreshing results!!!");
						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), ex.getMessage());

						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						ex.printStackTrace(pw);
						String stacktrace = pw.toString();

						if (stacktrace.length() < 2000) {
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), stacktrace);
						}

						ObjectMapper mapper = new ObjectMapper();
						try {
							String serializedEx = mapper.writeValueAsString(ex);

							if (serializedEx.length() < 2000) {
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), serializedEx);
							}
						} catch (JsonProcessingException ignored) {
							// ignored
						}
					} catch (Exception ex2) {
						logger.error(ex2);
					}
				} catch (Throwable t) {
					logger.error(t);
				}
			}

//			logger.info("results refresh successful");
			alreadyRunning = false;
		}
	}

	private void controlOBS(Account account) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		String prefix = configurationRepository.findByConfigName(ConfigurationRepository.streamPrefix).stream().map(Configuration::getConfigValue).findFirst().orElse(null);
		logger.info("prefix name: {}", prefix);
		if (prefix != null) {
			String gameSceneName = configurationRepository.findByConfigName(prefix + ConfigurationRepository.gameSceneName)
					.stream().map(Configuration::getConfigValue).findFirst().orElse(null);
			String resultsSceneName = configurationRepository.findByConfigName(prefix + ConfigurationRepository.resultsSceneName)
					.stream().map(Configuration::getConfigValue).findFirst().orElse(null);

			logger.info("game scene name: {}", gameSceneName);
			logger.info("results scene name: {}", resultsSceneName);

			Splatoon2Rotation rotation = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(Instant.now().getEpochSecond(),
					Instant.now().getEpochSecond(),
					Splatoon2Mode.Ranked);

			SplatNetXRankLeaderBoard leaderBoard = peaksExporter.getLeaderBoard(account, year, month);
			Double currentPower = getCurrentPower(leaderBoard, rotation);
			logger.info("current power: {}", currentPower);
			if (currentPower != null) {
				Splatoon2Match match = matchRepository.findTop1ByAccountIdAndModeAndRuleOrderByStartTimeDesc(account.getId(), Splatoon2Mode.Ranked, rotation.getRule());
				logger.info("match != null: {}", match != null);
				if (match != null) {
					if (match.getXPower() == null || !match.getXPower().equals(currentPower)
							|| matchRepository.findByAccountIdAndStartTimeGreaterThanEqualAndMode
							(
									account.getId(),
									extendedStatisticsExporter.getStarted().getEpochSecond() > rotation.getStartTime()
											? extendedStatisticsExporter.getStarted().getEpochSecond()
											: rotation.getStartTime()
									, Splatoon2Mode.Ranked)
							.size() == 0) {
						logger.info("1 trying to switch to scene: {}", gameSceneName);
						obsSceneSwitcher.switchScene(gameSceneName);
					} else {
						logger.info("2 trying to switch to scene: {}", resultsSceneName);
						obsSceneSwitcher.switchScene(resultsSceneName);
					}
				}
			} else {
				logger.info("3 trying to switch to scene: {}", gameSceneName);
				obsSceneSwitcher.switchScene(gameSceneName);
			}
		}
	}

	private Double getCurrentPower(SplatNetXRankLeaderBoard result, Splatoon2Rotation rotation) {
		Double power = null;

		switch (rotation.getRule()) {
			case Rainmaker:
				if (result.getRainmaker().getMy_ranking() != null) {
					power = result.getRainmaker().getMy_ranking().getX_power();
				}
				break;
			case TowerControl:
				if (result.getTower_control().getMy_ranking() != null) {
					power = result.getTower_control().getMy_ranking().getX_power();
				}
				break;
			case ClamBlitz:
				if (result.getClam_blitz().getMy_ranking() != null) {
					power = result.getClam_blitz().getMy_ranking().getX_power();
				}
				break;
			case SplatZones:
			default:
				if (result.getSplat_zones().getMy_ranking() != null) {
					power = result.getSplat_zones().getMy_ranking().getX_power();
				}
				break;
		}

		return power;
	}

	private List<Splatoon2AbilityMatch> parseAbilities(SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer.SplatNetGearSkills skills, String gearKind, long matchId) {
		List<Splatoon2AbilityMatch> abilitiesUsed = new ArrayList<>();

		abilitiesUsed.add(createAbilityMatch(0, skills.getMain(), gearKind, matchId));

		for (int i = 0; i < skills.getSubs().length; i++) {
			if (skills.getSubs()[i] != null) {
				abilitiesUsed.add(createAbilityMatch(i + 1, skills.getSubs()[i], gearKind, matchId));
			} else {
				System.out.println("this gear does not have 3 sub slots");
			}
		}

		return abilitiesUsed;
	}

	private Splatoon2AbilityMatch createAbilityMatch(int position, SplatNetGearSkill skill, String gearKind, long matchId) {
		Splatoon2AbilityMatch abilityUsed = new Splatoon2AbilityMatch();
		abilityUsed.setMatchId(matchId);
		abilityUsed.setAbilityId(abilityExporter.loadGear(skill).getId());
		abilityUsed.setKind(Splatoon2GearType.getGearTypeByKey(gearKind));
		abilityUsed.setGearPosition(position);

		return abilityUsed;
	}

	private void refreshMonthlyRankedResults(long accountId, List<SplatNetMatchResult> results) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		Splatoon2MonthlyResult result = monthlyResultRepository.findByAccountIdAndPeriodYearAndPeriodMonth(accountId, year, month);

		if (result != null) {
			boolean isDirty = false;

			List<SplatNetMatchResult> rankedMatches = results.stream()
					.filter(r -> Splatoon2Mode.getModeByName(r.getGame_mode().getKey()) == Splatoon2Mode.Ranked)
					.collect(Collectors.toList());

			for (SplatNetMatchResult rankedMatch : rankedMatches) {
				Splatoon2Rule rule = Splatoon2Rule.getRuleByName(rankedMatch.getRule().getKey());

				if (rankedMatch.getX_power() != null) {
					switch (rule) {
						case SplatZones:
							if (!Objects.equals(result.getZonesCurrent(), rankedMatch.getX_power())) {
								result.setZonesCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Current zones power for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesCurrent()));
								isDirty = true;
							}

							if (result.getZonesPeak() == null || result.getZonesPeak() < rankedMatch.getX_power()) {
								result.setZonesPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Zones peak for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getZonesPeak()));
								isDirty = true;
							}
							break;
						case Rainmaker:
							if (!Objects.equals(result.getRainmakerCurrent(), rankedMatch.getX_power())) {
								result.setRainmakerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Current rainmaker power for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getRainmakerCurrent()));
								isDirty = true;
							}

							if (result.getRainmakerPeak() == null || result.getRainmakerPeak() < rankedMatch.getX_power()) {
								result.setRainmakerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Rainmaker peak for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getRainmakerPeak()));
								isDirty = true;
							}
							break;
						case TowerControl:
							if (!Objects.equals(result.getTowerCurrent(), rankedMatch.getX_power())) {
								result.setTowerCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Current tower power for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getTowerCurrent()));
								isDirty = true;
							}

							if (result.getTowerPeak() == null || result.getTowerPeak() < rankedMatch.getX_power()) {
								result.setTowerPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Tower peak for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getTowerPeak()));
								isDirty = true;
							}
							break;
						case ClamBlitz:
							if (!Objects.equals(result.getClamsCurrent(), rankedMatch.getX_power())) {
								result.setClamsCurrent(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Current clams power for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getClamsCurrent()));
								isDirty = true;
							}

							if (result.getClamsPeak() == null || result.getClamsPeak() < rankedMatch.getX_power()) {
								result.setClamsPeak(rankedMatch.getX_power());
								discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
										String.format("Clams peak for month **%d-%d** is now **%.1f**.",
												result.getPeriodYear(),
												result.getPeriodMonth(),
												result.getClamsPeak()));
								isDirty = true;
							}
							break;
						default:
							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
									String.format("Error: received invalid rule **%s** for ranked mode.", rule));
							break;
					}
				}
			}

			if (isDirty) {
				monthlyResultRepository.save(result);
			}
		}
	}
}

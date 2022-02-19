package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonGearType;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.obs.ObsSceneSwitcher;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final Statistics statistics;

	private boolean alreadyRunning = false;
	private boolean isStreamRunning = false;
	private boolean isRankedRunning = false;

	public ResultsExporter() {
		String path = Paths.get(".").toAbsolutePath().normalize().toString();
		statistics = new Statistics(String.format("%s\\src\\main\\resources\\html\\template-example.html", path));
	}

	public void setRankedRunning(boolean rankedRunning) {
		isRankedRunning = rankedRunning;
	}

	private SplatoonMatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(SplatoonMatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private SplatoonRotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(SplatoonRotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private SplatoonAbilityMatchRepository abilityMatchRepository;

	@Autowired
	public void setAbilityMatchRepository(SplatoonAbilityMatchRepository abilityMatchRepository) {
		this.abilityMatchRepository = abilityMatchRepository;
	}

	private RequestSender splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(RequestSender splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private SplatoonMonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(SplatoonMonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private SplatoonClipRepository clipRepository;

	@Autowired
	public void setClipRepository(SplatoonClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
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

	public String getHtml() {
		return statistics.getCurrentHtml();
	}

	public void start() {
		isStreamRunning = true;
		statistics.reset();

		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		SplatoonMonthlyResult result = monthlyResultRepository.findByPeriodYearAndPeriodMonth(year, month);
		Map<SplatoonRule, Double> startPowers = new HashMap<>() {{
			put(SplatoonRule.SplatZones, result.getZonesCurrent());
			put(SplatoonRule.Rainmaker, result.getRainmakerCurrent());
			put(SplatoonRule.TowerControl, result.getTowerCurrent());
			put(SplatoonRule.ClamBlitz, result.getClamsCurrent());
		}};
		extendedStatisticsExporter.start(Instant.now(), startPowers);
	}

	public void stop() {
		isStreamRunning = false;
		isRankedRunning = false;
		statistics.stop();
		extendedStatisticsExporter.end();
	}

	public boolean isStreamRunning() {
		return isStreamRunning;
	}

	@Scheduled(cron = "*/10 * * * * *")
//	@Scheduled(fixedRate = 10000, initialDelay = 90000)
	public void loadGameResultsScheduled() {
		logger.debug("running results exporter");
		if (!alreadyRunning) {
			alreadyRunning = true;
			logger.info("loading results");

			try {
				logger.info("1");
				SplatNetMatchResultsCollection collection = splatoonResultsLoader.querySplatoonApi("/api/results", SplatNetMatchResultsCollection.class);

				logger.info("2");
				if (collection != null) {
					List<SplatNetMatchResult> results = new ArrayList<>();
					for (int i = collection.getResults().length - 1; i >= 0; i--) {
						results.add(collection.getResults()[i]);
					}

					logger.info("3");
					results = results.stream()
							.filter(r -> matchRepository.findByBattleNumber(r.getBattle_number()) == null)
							.collect(Collectors.toList());
					logger.info("3 test");

					for (SplatNetMatchResult singleResult : results) {
						logger.info("4");
						SplatNetMatchResult loadedMatch
								= splatoonResultsLoader.querySplatoonApi(String.format("/api/results/%s", singleResult.getBattle_number()), SplatNetMatchResult.class);

						SplatoonMatch match = new SplatoonMatch();
						match.setBattleNumber(loadedMatch.getBattle_number());

						match.setStartTime(loadedMatch.getStart_time());
						match.setElapsedTime(loadedMatch.getElapsed_time());
						match.setEndTime(loadedMatch.getStart_time() + loadedMatch.getElapsed_time());

						match.setStageId(stagesExporter.loadStage(loadedMatch.getStage()).getId());
						match.setMode(SplatoonMode.getModeByName(loadedMatch.getGame_mode().getKey()));
						match.setRule(SplatoonRule.getRuleByName(loadedMatch.getRule().getKey()));

						logger.info("5");
						SplatoonRotation rotation
								= rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(match.getStartTime(), match.getEndTime(), match.getMode());

						logger.info("6");
						if (rotation != null
								&& (Objects.equals(rotation.getStageAId(), match.getStageId()) || Objects.equals(rotation.getStageBId(), match.getStageId()))) {
							match.setRotationId(rotation.getId());
						}

						if (loadedMatch.getUdemae() != null) {
							match.setRank(loadedMatch.getUdemae().getName());
						}

						match.setXPower(loadedMatch.getX_power());
						match.setXPowerEstimate(loadedMatch.getEstimate_gachi_power());
						match.setXLobbyPower(loadedMatch.getEstimate_x_power());

						match.setLeagueTag(loadedMatch.getTag_id());
						match.setLeaguePower(loadedMatch.getLeague_point());
						match.setLeaguePowerMax(loadedMatch.getMax_league_point());
						match.setLeaguePowerEstimate(loadedMatch.getMy_estimate_league_point());
						match.setLeagueEnemyPower(loadedMatch.getOther_estimate_league_point());

						logger.info("7");
						SplatoonWeapon weapon = weaponExporter.loadWeapon(loadedMatch.getPlayer_result().getPlayer().getWeapon());
						logger.info("8");

						match.setWeaponId(weapon.getId());
						match.setTurfGain(loadedMatch.getPlayer_result().getGame_paint_point());
						match.setTurfTotal(loadedMatch.getWeapon_paint_point());

						match.setKills(loadedMatch.getPlayer_result().getKill_count());
						match.setAssists(loadedMatch.getPlayer_result().getAssist_count());
						match.setDeaths(loadedMatch.getPlayer_result().getDeath_count());
						match.setSpecials(loadedMatch.getPlayer_result().getSpecial_count());

						match.setOwnScore(loadedMatch.getMy_team_count());
						match.setEnemyScore(loadedMatch.getOther_team_count());

						match.setOwnPercentage(loadedMatch.getMy_team_percentage());
						match.setEnemyPercentage(loadedMatch.getOther_team_percentage());

						match.setMatchResult(SplatoonMatchResult.parseResult(loadedMatch.getMy_team_result().getKey()));
						match.setIsKo(loadedMatch.getMy_team_count() != null && loadedMatch.getOther_team_count() != null
								&& (loadedMatch.getMy_team_count() == 100 || loadedMatch.getOther_team_count() == 100));

						match.setHeadgearId(gearExporter.loadGear(loadedMatch.getPlayer_result().getPlayer().getHead()).getId());
						match.setClothesId(gearExporter.loadGear(loadedMatch.getPlayer_result().getPlayer().getClothes()).getId());
						match.setShoesId(gearExporter.loadGear(loadedMatch.getPlayer_result().getPlayer().getShoes()).getId());

						match.setMatchResultOverview(singleResult);
						match.setMatchResultDetails(loadedMatch);

						logger.info("9");
						matchRepository.save(match);
						logger.info("10");

						weapon.setTurf(loadedMatch.getWeapon_paint_point());
						if (match.getMatchResult() == SplatoonMatchResult.Win) {
							weapon.setWins(weapon.getWins() + 1);
						} else {
							weapon.setDefeats(weapon.getDefeats() + 1);
						}

						logger.info("11");
						weaponRepository.save(weapon);
						logger.info("12");

						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
								String.format("Put new Match with id **%d** for mode **%s** and rule **%s** into database. It was a **%s**.",
										match.getId(),
										match.getMode(),
										match.getRule(),
										match.getMatchResult()));
						logger.info("13");

						List<SplatoonAbilityMatch> abilitiesUsedInMatch = new ArrayList<>();

						abilitiesUsedInMatch.addAll(parseAbilities(
								loadedMatch.getPlayer_result().getPlayer().getHead_skills(),
								loadedMatch.getPlayer_result().getPlayer().getHead().getKind(),
								match.getId()));
						abilitiesUsedInMatch.addAll(parseAbilities(
								loadedMatch.getPlayer_result().getPlayer().getClothes_skills(),
								loadedMatch.getPlayer_result().getPlayer().getClothes().getKind(),
								match.getId()));
						abilitiesUsedInMatch.addAll(parseAbilities(
								loadedMatch.getPlayer_result().getPlayer().getShoes_skills(),
								loadedMatch.getPlayer_result().getPlayer().getShoes().getKind(),
								match.getId()));

						logger.info("14");
						abilityMatchRepository.saveAll(abilitiesUsedInMatch);
						logger.info("15");

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
										"- Paint: + **%d** points\n",
								match.getMode(),
								match.getRule(),
								match.getMatchResult(),
								loadedMatch.getPlayer_result().getPlayer().getWeapon().getName(),
								match.getOwnPercentage() != null ? match.getOwnPercentage() : match.getOwnScore(),
								match.getEnemyPercentage() != null ? match.getEnemyPercentage() : match.getEnemyScore(),

								match.getKills(),
								match.getAssists(),
								match.getDeaths(),
								match.getTurfGain());

						logger.info("16");
						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), discordResultMessage);
						logger.info("17");

						// refresh clips and send them to discord
						List<SplatoonClip> clips = clipRepository.getAllByStartTimeIsGreaterThanAndEndTimeIsLessThan(match.getStartTime(), match.getEndTime());
						logger.info("18");
						if (clips.size() > 0) {
							StringBuilder ratingsMessageBuilder = new StringBuilder("**Viewers rated my performance**:\n");

							for (SplatoonClip clip : clips) {
								ratingsMessageBuilder.append(String.format("\n- **%s** play - Clip: <%s> - Description: \"%s\"",
										clip.getIsGoodPlay() ? "GOOD" : "BAD",
										clip.getClipUrl(),
										clip.getDescription()));

								clip.setMatchId(match.getId());
							}

							discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getMatchChannelName(), ratingsMessageBuilder.toString());
							logger.info("19");
							clipRepository.saveAll(clips);
							logger.info("20");
						}

						logger.info("21");
						discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), String.format("Added used abilities to Match with id **%d**", match.getId()));
						logger.info("22");
					}

					// TODO prüfen, ob hier dann auch definitv alle Matches des Streams ankommen!!
					if (isStreamRunning) {
						logger.info("23");
						statistics.addMatches(results);
						statistics.exportHtml();
						logger.info("24");
					}

					logger.info("25");
					refreshMonthlyRankedResults(results);
					logger.info("26");

					if (isStreamRunning) {
						logger.info("27");
						extendedStatisticsExporter.export();
						logger.info("28");
					}
				}

				// TODO automatic scene switch in obs
				// TODO load current ranked results from splatnet
				// TODO check if current mode != null
				// TODO in that case, check if current mode != last match power (refreshMonthlyRankedResults)
				// TODO if != : switch to game scene
				// TODO if == : switch to results overview scene
				if (isStreamRunning && isRankedRunning) {
					controlOBS();
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

			logger.info("results refresh successful");
			alreadyRunning = false;
		}
	}

	private void controlOBS() {
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

			SplatoonRotation rotation = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(Instant.now().getEpochSecond(),
					Instant.now().getEpochSecond(),
					SplatoonMode.Ranked);

			SplatNetXRankLeaderBoard leaderBoard = peaksExporter.getLeaderBoard(year, month);
			Double currentPower = getCurrentPower(leaderBoard, rotation);
			logger.info("current power: {}", currentPower);
			if (currentPower != null) {
				SplatoonMatch match = matchRepository.findTop1ByModeAndRuleOrderByStartTimeDesc(SplatoonMode.Ranked, rotation.getRule());
				logger.info("match != null: {}", match != null);
				if (match != null) {
					if (match.getXPower() == null || !match.getXPower().equals(currentPower)
							|| matchRepository.findByStartTimeGreaterThanEqualAndMode(
									extendedStatisticsExporter.getStarted().getEpochSecond() > rotation.getStartTime()
											? extendedStatisticsExporter.getStarted().getEpochSecond()
											: rotation.getStartTime()
							, SplatoonMode.Ranked).size() == 0) {
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

	private Double getCurrentPower(SplatNetXRankLeaderBoard result, SplatoonRotation rotation) {
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

	private List<SplatoonAbilityMatch> parseAbilities(SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer.SplatNetGearSkills skills, String gearKind, long matchId) {
		List<SplatoonAbilityMatch> abilitiesUsed = new ArrayList<>();

		abilitiesUsed.add(createAbilityMatch(0, skills.getMain(), gearKind, matchId));

		for (int i = 0; i < skills.getSubs().length; i++) {
			if (skills.getSubs()[i] != null) {
				abilitiesUsed.add(createAbilityMatch(i + 1, skills.getSubs()[i], gearKind, matchId));
			} else {
				System.out.println("nix");
			}
		}

		return abilitiesUsed;
	}

	private SplatoonAbilityMatch createAbilityMatch(int position, SplatNetGearSkill skill, String gearKind, long matchId) {
		SplatoonAbilityMatch abilityUsed = new SplatoonAbilityMatch();
		abilityUsed.setMatchId(matchId);
		abilityUsed.setAbilityId(abilityExporter.loadGear(skill).getId());
		abilityUsed.setKind(SplatoonGearType.getGearTypeByKey(gearKind));
		abilityUsed.setGearPosition(position);

		return abilityUsed;
	}

	private void refreshMonthlyRankedResults(List<SplatNetMatchResult> results) {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());
		int year = date.getYear();
		int month = date.getMonthValue();

		SplatoonMonthlyResult result = monthlyResultRepository.findByPeriodYearAndPeriodMonth(year, month);

		if (result != null) {
			boolean isDirty = false;

			List<SplatNetMatchResult> rankedMatches = results.stream()
					.filter(r -> SplatoonMode.getModeByName(r.getGame_mode().getKey()) == SplatoonMode.Ranked)
					.collect(Collectors.toList());

			for (SplatNetMatchResult rankedMatch : rankedMatches) {
				SplatoonRule rule = SplatoonRule.getRuleByName(rankedMatch.getRule().getKey());

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
		} else {
			discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), "Error: a monthly result for this month does NOT exist!");
		}
	}
}

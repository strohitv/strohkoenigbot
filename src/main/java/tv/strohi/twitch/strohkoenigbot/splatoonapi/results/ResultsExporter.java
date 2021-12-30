package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonAbilityMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMonthlyResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonRotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonGearType;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonAbilityMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMonthlyResultRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGearSkill;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResultsCollection;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.Statistics;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ResultsExporter {
	private final Statistics statistics;

	private boolean alreadyRunning = false;
	private boolean isStreamRunning = false;

	public ResultsExporter() {
		String path = Paths.get(".").toAbsolutePath().normalize().toString();
		statistics = new Statistics(String.format("%s\\src\\main\\resources\\html\\template-example.html", path));
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

	public String getHtml() {
		return statistics.getCurrentHtml();
	}

	public void start() {
		isStreamRunning = true;
		statistics.reset();
	}

	public void stop() {
		isStreamRunning = false;
		statistics.stop();
	}

	@Scheduled(fixedRate = 15000, initialDelay = 90000)
	public void loadGameResultsScheduled() {
		if (!alreadyRunning) {
			alreadyRunning = true;

			SplatNetMatchResultsCollection collection = splatoonResultsLoader.querySplatoonApi("/api/results", SplatNetMatchResultsCollection.class);

			List<SplatNetMatchResult> results = new ArrayList<>();
			for (int i = collection.getResults().length - 1; i >= 0; i--) {
				results.add(collection.getResults()[i]);
			}

			// TODO: Sobald ich die Datenbanktabelle für Matches habe: statt nach lastAnalysedMatchStart
			// TODO: nach den Match-Keys filtern, die NICHT in der Tabelle enthalten sind.
			results = results.stream()
					.filter(r -> matchRepository.findByBattleNumber(r.getBattle_number()) == null)
					.collect(Collectors.toList());

			for (SplatNetMatchResult singleResult : results) {
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

				SplatoonRotation rotation
						= rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(match.getStartTime(), match.getEndTime(), match.getMode());

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

				match.setWeaponId(weaponExporter.loadWeapon(loadedMatch.getPlayer_result().getPlayer().getWeapon()).getId());
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

				matchRepository.save(match);

				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
						String.format("Put new Match with id **%d** for mode **%s** and rule **%s** into database. It was a **%s**.",
								match.getId(),
								match.getMode(),
								match.getRule(),
								match.getMatchResult()));

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

				abilityMatchRepository.saveAll(abilitiesUsedInMatch);

				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(), String.format("Added used abilities to Match with id **%d**", match.getId()));
			}

			// TODO prüfen, ob hier dann auch definitv alle Matches des Streams ankommen!!
			if (isStreamRunning) {
				statistics.addMatches(results);
				statistics.exportHtml();
			}

			refreshMonthlyRankedResults(results);

			alreadyRunning = false;
		}
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
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault()).minus(5, ChronoUnit.DAYS);
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

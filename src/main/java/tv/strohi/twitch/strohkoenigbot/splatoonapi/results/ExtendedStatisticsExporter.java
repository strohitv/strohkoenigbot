package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGearSkill;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;

import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class ExtendedStatisticsExporter {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final static String imageHost = "https://app.splatoon2.nintendo.net";
	private final ObjectMapper mapper = new ObjectMapper();

	private String currentHtml = "<!DOCTYPE html>\n" +
			"<html lang=\"en\">\n" +
			"\n" +
			"<head>\n" +
			"\t<meta charset=\"UTF-8\">\n" +
			"\t<meta http-equiv=\"refresh\" content=\"5\">\n" +
			"\t<title>Splatoon 2 statistics</title>\n" +
			"</head>\n" +
			"<body>\n" +
			"</body>\n" +
			"</html>";

	public String getCurrentHtml() {
		return currentHtml;
	}

	private Instant started = Instant.now();

	public Instant getStarted() {
		return started;
	}

	private Map<Splatoon2Rule, Double> powersBeforeStream;

	private String path;

	public void setPath(String path) {
		this.path = path;
	}

	private Splatoon2RotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(Splatoon2RotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private Splatoon2MatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(Splatoon2MatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private Splatoon2MonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(Splatoon2MonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private Splatoon2StageRepository stageRepository;

	@Autowired
	public void setStageRepository(Splatoon2StageRepository stageRepository) {
		this.stageRepository = stageRepository;
	}

	private Splatoon2WeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(Splatoon2WeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
	}

	private Splatoon2GearRepository gearRepository;

	@Autowired
	public void setGearRepository(Splatoon2GearRepository gearRepository) {
		this.gearRepository = gearRepository;
	}

	private ResourcesDownloader resourcesDownloader;

	@Autowired
	public void setResourcesDownloader(ResourcesDownloader resourcesDownloader) {
		this.resourcesDownloader = resourcesDownloader;
	}

	public ExtendedStatisticsExporter() {
		String homePath = Paths.get(".").toAbsolutePath().normalize().toString();
		path = String.format("%s\\src\\main\\resources\\html\\template-fullscreen-overlay-example.html", homePath);
	}

	public void start(Instant startTime, Map<Splatoon2Rule, Double> streamStartPowers) {
		started = startTime;
		powersBeforeStream = streamStartPowers;
	}

	public void end() {
		started = null;
	}

	public void export(long accountId) {
		Splatoon2Rotation currentRanked = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(
				Instant.now().getEpochSecond(),
				Instant.now().getEpochSecond(),
				Splatoon2Mode.Ranked
		);

		Splatoon2Rotation nextRanked = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(
				currentRanked.getEndTime() + 100,
				currentRanked.getEndTime() + 100,
				Splatoon2Mode.Ranked
		);

		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());

		int currentYear = date.getYear();
		int currentMonth = date.getMonthValue();
		Splatoon2MonthlyResult currentMonthResult = monthlyResultRepository.findByAccountIdAndPeriodYearAndPeriodMonth(accountId, currentYear, currentMonth);

		int previousPeriodYear = date.minusDays(date.getDayOfMonth() + 5).getYear();
		int previousPeriodMonth = date.minusDays(date.getDayOfMonth() + 5).getMonthValue();
		Splatoon2MonthlyResult lastMonthResult = monthlyResultRepository.findByAccountIdAndPeriodYearAndPeriodMonth(accountId, previousPeriodYear, previousPeriodMonth);

		List<Splatoon2Match> allStreamMatches = matchRepository.findByAccountIdAndStartTimeGreaterThanEqualAndMode(accountId, started.getEpochSecond(), Splatoon2Mode.Ranked);
		List<Splatoon2Match> allMonthMatches = matchRepository.findByAccountIdAndStartTimeGreaterThanEqualAndMode(accountId, currentMonthResult.getStartTime(), Splatoon2Mode.Ranked);

		if (allStreamMatches.size() == 0) {
			return;
		}

		Splatoon2Match lastMatch = allStreamMatches.stream()
				.max(Comparator.comparing(Splatoon2Match::getStartTime))
				.orElse(new Splatoon2Match());
		Splatoon2Stage stageA = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(currentRanked.getStageAId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Stage());
		Splatoon2Stage stageB = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(currentRanked.getStageBId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Stage());

		Splatoon2Stage nextStageA = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(nextRanked.getStageAId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Stage());
		Splatoon2Stage nextStageB = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(nextRanked.getStageBId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Stage());

		Splatoon2Weapon lastMatchWeapon = StreamSupport.stream(weaponRepository.findAllById(Collections.singletonList(lastMatch.getWeaponId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Weapon());

		Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(lastMatchWeapon.getId(), accountId).orElse(new Splatoon2WeaponStats(0L, 0L, 0L, 0L, 0, 0));

		Splatoon2Gear lastMatchHead = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getHeadgearId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Gear());
		Splatoon2Gear lastMatchClothes = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getClothesId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Gear());
		Splatoon2Gear lastMatchShoes = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getShoesId())).spliterator(), false)
				.findFirst()
				.orElse(new Splatoon2Gear());

		Map<Splatoon2GearType, List<String>> perkImages = new HashMap<>() {
			{
				put(Splatoon2GearType.Head, new ArrayList<>());
				put(Splatoon2GearType.Clothes, new ArrayList<>());
				put(Splatoon2GearType.Shoes, new ArrayList<>());
			}
		};

		SplatNetMatchResult result = null;
		if (lastMatch.getJsonMatch() != null) {
			try {
				result = mapper.readValue(lastMatch.getJsonMatch(), SplatNetMatchResult.class);
			} catch (Exception ignored) {
				// ignored
			}
		}

		if (result != null) {
			perkImages.get(Splatoon2GearType.Head).add(result.getPlayer_result().getPlayer().getHead_skills().getMain().getImage());
			perkImages.get(Splatoon2GearType.Head).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getHead_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));

			perkImages.get(Splatoon2GearType.Clothes).add(result.getPlayer_result().getPlayer().getClothes_skills().getMain().getImage());
			perkImages.get(Splatoon2GearType.Clothes).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getClothes_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));

			perkImages.get(Splatoon2GearType.Shoes).add(result.getPlayer_result().getPlayer().getShoes_skills().getMain().getImage());
			perkImages.get(Splatoon2GearType.Shoes).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getShoes_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));
		} else {
			perkImages.get(Splatoon2GearType.Head).addAll(Arrays.asList("", ""));
			perkImages.get(Splatoon2GearType.Clothes).addAll(Arrays.asList("", ""));
			perkImages.get(Splatoon2GearType.Shoes).addAll(Arrays.asList("", ""));
		}

		long stageAWins = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageA.getId())
				.filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win)
				.count();
		long stageADefeats = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageA.getId())
				.filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win)
				.count();

		long stageBWins = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageB.getId())
				.filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win)
				.count();
		long stageBDefeats = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageB.getId())
				.filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win)
				.count();

		long allMonthRuleWins = allMonthMatches.stream()
				.filter(m -> m.getRule() == lastMatch.getRule())
				.filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win)
				.count();
		long allMonthRuleDefeats = allMonthMatches.stream()
				.filter(m -> m.getRule() == lastMatch.getRule())
				.filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win)
				.count();

		long allMonthWins = allMonthMatches.stream()
				.filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win)
				.count();
		long allMonthDefeats = allMonthMatches.stream()
				.filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win)
				.count();

		long weaponPointsGain = allStreamMatches.stream()
				.filter(m -> m.getMode() == Splatoon2Mode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.mapToInt(Splatoon2Match::getTurfGain)
				.sum();
		long weaponWins = allStreamMatches.stream()
				.filter(m -> m.getMode() == Splatoon2Mode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.filter(m -> m.getMatchResult() == Splatoon2MatchResult.Win)
				.count();
		long weaponDefeats = allStreamMatches.stream()
				.filter(m -> m.getMode() == Splatoon2Mode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.filter(m -> m.getMatchResult() != Splatoon2MatchResult.Win)
				.count();

		List<Splatoon2MonthlyResult> allPeaks = monthlyResultRepository.findAllByAccountId(accountId).stream()
				.filter(mr -> mr.getZonesPeak() != null || mr.getRainmakerPeak() != null || mr.getTowerPeak() != null || mr.getClamsPeak() != null)
				.collect(Collectors.toList());

		double zonesPeak = allPeaks.stream()
				.filter(mr -> mr.getZonesPeak() != null)
				.mapToDouble(Splatoon2MonthlyResult::getZonesPeak)
				.max()
				.orElse(0.0);
		double rainmakerPeak = allPeaks.stream()
				.filter(mr -> mr.getRainmakerPeak() != null)
				.mapToDouble(Splatoon2MonthlyResult::getRainmakerPeak)
				.max()
				.orElse(0.0);
		double towerPeak = allPeaks.stream()
				.filter(mr -> mr.getTowerPeak() != null)
				.mapToDouble(Splatoon2MonthlyResult::getTowerPeak)
				.max()
				.orElse(0.0);
		double clamsPeak = allPeaks.stream()
				.filter(mr -> mr.getClamsPeak() != null)
				.mapToDouble(Splatoon2MonthlyResult::getClamsPeak)
				.max()
				.orElse(0.0);

		String currentPowerDifferenceSign = "";
		String currentPowerDifference = "";
		if (powersBeforeStream.get(lastMatch.getRule()) != null && lastMatch.getXPower() != null) {
			currentPowerDifferenceSign = powersBeforeStream.get(lastMatch.getRule()) <= lastMatch.getXPower() ? "+" : "-";
			currentPowerDifference = String.format("%.1f", Math.abs(lastMatch.getXPower() - powersBeforeStream.get(lastMatch.getRule())));
		}

		String lastMonthPowerDifferenceSign = "";
		String lastMonthPowerDifference = "";
		if (getPowerForRule(lastMonthResult, lastMatch.getRule()) != null && lastMatch.getXPower() != null) {
			lastMonthPowerDifferenceSign = getPowerForRule(lastMonthResult, lastMatch.getRule()) <= lastMatch.getXPower() ? "+" : "-";
			lastMonthPowerDifference = String.format("%.1f", Math.abs(lastMatch.getXPower() - getPowerForRule(lastMonthResult, lastMatch.getRule())));
		}

		InputStream is = this.getClass().getClassLoader().getResourceAsStream("html/template-fullscreen-overlay.html");

		try {
			assert is != null;
			currentHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

			currentHtml = currentHtml
					.replace("{main-weapon-points-gain}", String.format("%d", weaponPointsGain))
					.replace("{main-weapon-wins-gain}", String.format("%d", weaponWins))
					.replace("{main-weapon-defeats-gain}", String.format("%d", weaponDefeats))

					.replace("{main-weapon-points}", String.format("%d", lastMatch.getTurfTotal()))
					.replace("{main-weapon-wins}", String.format("%d", weaponStats.getWins()))
					.replace("{main-weapon-defeats}", String.format("%d", weaponStats.getDefeats()))

					.replace("{main-weapon}", String.format("%s", lastMatchWeapon.getName()))
					.replace("{main-weapon-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchWeapon.getImage())))
					.replace("{sub-weapon-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchWeapon.getSubImage())))
					.replace("{special-weapon-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchWeapon.getSpecialImage())))

					.replace("{head-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchHead.getImage())))
					.replace("{head-main-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Head).get(0))))
					.replace("{head-sub-1-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Head).get(1))))

					.replace("{clothing-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchClothes.getImage())))
					.replace("{clothing-main-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Clothes).get(0))))
					.replace("{clothing-sub-1-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Clothes).get(1))))

					.replace("{shoes-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, lastMatchShoes.getImage())))
					.replace("{shoes-main-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Shoes).get(0))))
					.replace("{shoes-sub-1-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Shoes).get(1))))

					.replace("{zones-power-current}", currentMonthResult.getZonesCurrent() != null ? String.format("%4.1f", currentMonthResult.getZonesCurrent()) : "")
					.replace("{rainmaker-power-current}", currentMonthResult.getRainmakerCurrent() != null ? String.format("%4.1f", currentMonthResult.getRainmakerCurrent()) : "")
					.replace("{tower-power-current}", currentMonthResult.getTowerCurrent() != null ? String.format("%4.1f", currentMonthResult.getTowerCurrent()) : "")
					.replace("{clams-power-current}", currentMonthResult.getClamsCurrent() != null ? String.format("%4.1f", currentMonthResult.getClamsCurrent()) : "")

					.replace("{zones-power-peak}", String.format("%4.1f", zonesPeak))
					.replace("{rainmaker-power-peak}", String.format("%4.1f", rainmakerPeak))
					.replace("{tower-power-peak}", String.format("%4.1f", towerPeak))
					.replace("{clams-power-peak}", String.format("%4.1f", clamsPeak))

					.replace("{game-mode-power-difference-sign-current}", currentPowerDifferenceSign)
					.replace("{game-mode-power-difference-current}", currentPowerDifference)

					.replace("{game-rule-wins-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == Splatoon2Mode.Ranked)
							.filter(sm -> sm.getRule() == lastMatch.getRule())
							.filter(sm -> sm.getMatchResult() == Splatoon2MatchResult.Win)
							.count()))
					.replace("{game-rule-defeats-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == Splatoon2Mode.Ranked)
							.filter(sm -> sm.getRule() == lastMatch.getRule())
							.filter(sm -> sm.getMatchResult() != Splatoon2MatchResult.Win)
							.count()))
					.replace("{ranked-wins-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == Splatoon2Mode.Ranked)
							.filter(sm -> sm.getMatchResult() == Splatoon2MatchResult.Win)
							.count()))
					.replace("{ranked-defeats-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == Splatoon2Mode.Ranked)
							.filter(sm -> sm.getMatchResult() != Splatoon2MatchResult.Win)
							.count()))

					// Todo eventuell nachliefern
//					.replace("{disconnect-count-stream}", allStreamMatches.stream().filter(sm -> sm.getd))

					.replace("{game-mode-power-difference-sign-last-month}", lastMonthPowerDifferenceSign)
					.replace("{game-mode-power-difference-last-month}", lastMonthPowerDifference)

					.replace("{game-rule-wins-last-month}", String.format("%d", allMonthRuleWins))
					.replace("{game-rule-defeats-last-month}", String.format("%d", allMonthRuleDefeats))
					.replace("{ranked-wins-last-month}", String.format("%d", allMonthWins))
					.replace("{ranked-defeats-last-month}", String.format("%d", allMonthDefeats))

					// Todo eventuell nachliefern
//					.replace("{disconnect-count-last-month}", shoesGearSub1)

					.replace("{current-game-mode}", getGameRuleString(currentRanked.getRule()))
					.replace("{next-game-mode}", getGameRuleString(nextRanked.getRule()))

					.replace("{stage-a-wins}", String.format("%d", stageAWins))
					.replace("{stage-a-defeats}", String.format("%d", stageADefeats))
					.replace("{stage-a-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, stageA.getImage())))

					.replace("{stage-b-wins}", String.format("%d", stageBWins))
					.replace("{stage-b-defeats}", String.format("%d", stageBDefeats))
					.replace("{stage-b-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, stageB.getImage())))

					.replace("{next-rotation-stage-a-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, nextStageA.getImage())))
					.replace("{next-rotation-stage-b-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, nextStageB.getImage())));

			if (perkImages.get(Splatoon2GearType.Head).size() > 2) {
				currentHtml = currentHtml.replace("{head-sub-2-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Head).get(2))))
						.replace("{head-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{head-sub-2-image}", "")
						.replace("{head-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(Splatoon2GearType.Head).size() > 3) {
				currentHtml = currentHtml.replace("{head-sub-3-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Head).get(3))))
						.replace("{head-sub-3-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{head-sub-3-image}", "")
						.replace("{head-sub-3-hidden}", "hidden");
			}

			if (perkImages.get(Splatoon2GearType.Clothes).size() > 2) {
				currentHtml = currentHtml.replace("{clothing-sub-2-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Clothes).get(2))))
						.replace("{clothing-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{clothing-sub-2-image}", "")
						.replace("{clothing-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(Splatoon2GearType.Clothes).size() > 3) {
				currentHtml = currentHtml.replace("{clothing-sub-3-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Clothes).get(3))))
						.replace("{clothing-sub-3-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{clothing-sub-3-image}", "")
						.replace("{clothing-sub-3-hidden}", "hidden");
			}

			if (perkImages.get(Splatoon2GearType.Shoes).size() > 2) {
				currentHtml = currentHtml.replace("{shoes-sub-2-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Shoes).get(2))))
						.replace("{shoes-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{shoes-sub-2-image}", "")
						.replace("{shoes-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(Splatoon2GearType.Shoes).size() > 3) {
				currentHtml = currentHtml.replace("{shoes-sub-3-image}", resourcesDownloader.ensureExistsLocally(String.format("%s%s", imageHost, perkImages.get(Splatoon2GearType.Shoes).get(3))))
						.replace("{shoes-sub-3-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{shoes-sub-3-image}", "")
						.replace("{shoes-sub-3-hidden}", "hidden");
			}

			FileWriter myWriter = new FileWriter(path);
			myWriter.write(currentHtml);
			myWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Double getPowerForRule(Splatoon2MonthlyResult result, Splatoon2Rule rule) {
		Double power;

		if (rule != null) {
			switch (rule) {
				case Rainmaker:
					power = result.getRainmakerCurrent();
					break;
				case TowerControl:
					power = result.getTowerCurrent();
					break;
				case ClamBlitz:
					power = result.getClamsCurrent();
					break;
				case SplatZones:
				default:
					power = result.getZonesCurrent();
					break;
			}
		} else {
			power = result.getZonesCurrent();
		}

		return power;
	}

	private String getGameRuleString(Splatoon2Rule rule) {
		String result;

		switch (rule) {
			case Rainmaker:
				result = "Rainmaker";
				break;
			case TowerControl:
				result = "Tower Control";
				break;
			case ClamBlitz:
				result = "Clam Blitz";
				break;
			case SplatZones:
			default:
				result = "Splat Zones";
				break;
		}

		return result;
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonGearType;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.*;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGearSkill;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;

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

	private Map<SplatoonRule, Double> powersBeforeStream;

	private String path;

	public void setPath(String path) {
		this.path = path;
	}

	private SplatoonRotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(SplatoonRotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private SplatoonMatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(SplatoonMatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private SplatoonMonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(SplatoonMonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private SplatoonStageRepository stageRepository;

	@Autowired
	public void setStageRepository(SplatoonStageRepository stageRepository) {
		this.stageRepository = stageRepository;
	}

	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private SplatoonGearRepository gearRepository;

	@Autowired
	public void setGearRepository(SplatoonGearRepository gearRepository) {
		this.gearRepository = gearRepository;
	}

	public ExtendedStatisticsExporter() {
		String homePath = Paths.get(".").toAbsolutePath().normalize().toString();
		path = String.format("%s\\src\\main\\resources\\html\\template-fullscreen-overlay-example.html", homePath);
	}

	public void start(Instant startTime, Map<SplatoonRule, Double> streamStartPowers) {
		started = startTime;
		powersBeforeStream = streamStartPowers;
	}

	public void end() {
		started = null;
	}

	public void export() {
		SplatoonRotation currentRanked = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(
				Instant.now().getEpochSecond(),
				Instant.now().getEpochSecond(),
				SplatoonMode.Ranked
		);

		SplatoonRotation nextRanked = rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(
				currentRanked.getEndTime() + 100,
				currentRanked.getEndTime() + 100,
				SplatoonMode.Ranked
		);

		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault());

		int currentYear = date.getYear();
		int currentMonth = date.getMonthValue();
		SplatoonMonthlyResult currentMonthResult = monthlyResultRepository.findByPeriodYearAndPeriodMonth(currentYear, currentMonth);

		int previousPeriodYear = date.minusDays(date.getDayOfMonth() + 5).getYear();
		int previousPeriodMonth = date.minusDays(date.getDayOfMonth() + 5).getMonthValue();
		SplatoonMonthlyResult lastMonthResult = monthlyResultRepository.findByPeriodYearAndPeriodMonth(previousPeriodYear, previousPeriodMonth);

		List<SplatoonMatch> allStreamMatches = matchRepository.findByStartTimeGreaterThanEqualAndMode(started.getEpochSecond(), SplatoonMode.Ranked);
		List<SplatoonMatch> allMonthMatches = matchRepository.findByStartTimeGreaterThanEqualAndMode(currentMonthResult.getStartTime(), SplatoonMode.Ranked);

		SplatoonMatch lastMatch = allStreamMatches.stream()
				.max(Comparator.comparing(SplatoonMatch::getStartTime))
				.orElse(new SplatoonMatch());
		SplatoonStage stageA = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(currentRanked.getStageAId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonStage());
		SplatoonStage stageB = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(currentRanked.getStageBId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonStage());

		SplatoonStage nextStageA = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(nextRanked.getStageAId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonStage());
		SplatoonStage nextStageB = StreamSupport.stream(stageRepository.findAllById(Collections.singletonList(nextRanked.getStageBId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonStage());

		SplatoonWeapon lastMatchWeapon = StreamSupport.stream(weaponRepository.findAllById(Collections.singletonList(lastMatch.getWeaponId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonWeapon());

		SplatoonGear lastMatchHead = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getHeadgearId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonGear());
		SplatoonGear lastMatchClothes = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getClothesId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonGear());
		SplatoonGear lastMatchShoes = StreamSupport.stream(gearRepository.findAllById(Collections.singletonList(lastMatch.getShoesId())).spliterator(), false)
				.findFirst()
				.orElse(new SplatoonGear());

		Map<SplatoonGearType, List<String>> perkImages = new HashMap<>() {
			{
				put(SplatoonGearType.Head, new ArrayList<>());
				put(SplatoonGearType.Clothes, new ArrayList<>());
				put(SplatoonGearType.Shoes, new ArrayList<>());
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
			perkImages.get(SplatoonGearType.Head).add(result.getPlayer_result().getPlayer().getHead_skills().getMain().getImage());
			perkImages.get(SplatoonGearType.Head).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getHead_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));

			perkImages.get(SplatoonGearType.Clothes).add(result.getPlayer_result().getPlayer().getClothes_skills().getMain().getImage());
			perkImages.get(SplatoonGearType.Clothes).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getClothes_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));

			perkImages.get(SplatoonGearType.Shoes).add(result.getPlayer_result().getPlayer().getShoes_skills().getMain().getImage());
			perkImages.get(SplatoonGearType.Shoes).addAll(Arrays.stream(result.getPlayer_result().getPlayer().getShoes_skills().getSubs())
					.filter(Objects::nonNull)
					.map(SplatNetGearSkill::getImage)
					.collect(Collectors.toList()));
		} else {
			perkImages.get(SplatoonGearType.Head).addAll(Arrays.asList("", ""));
			perkImages.get(SplatoonGearType.Clothes).addAll(Arrays.asList("", ""));
			perkImages.get(SplatoonGearType.Shoes).addAll(Arrays.asList("", ""));
		}

		long stageAWins = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageA.getId())
				.filter(m -> m.getMatchResult() == SplatoonMatchResult.Win)
				.count();
		long stageADefeats = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageA.getId())
				.filter(m -> m.getMatchResult() != SplatoonMatchResult.Win)
				.count();

		long stageBWins = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageB.getId())
				.filter(m -> m.getMatchResult() == SplatoonMatchResult.Win)
				.count();
		long stageBDefeats = allStreamMatches.stream()
				.filter(m -> m.getStageId() == stageB.getId())
				.filter(m -> m.getMatchResult() != SplatoonMatchResult.Win)
				.count();

		long allMonthRuleWins = allMonthMatches.stream()
				.filter(m -> m.getRule() == lastMatch.getRule())
				.filter(m -> m.getMatchResult() == SplatoonMatchResult.Win)
				.count();
		long allMonthRuleDefeats = allMonthMatches.stream()
				.filter(m -> m.getRule() == lastMatch.getRule())
				.filter(m -> m.getMatchResult() != SplatoonMatchResult.Win)
				.count();

		long allMonthWins = allMonthMatches.stream()
				.filter(m -> m.getMatchResult() == SplatoonMatchResult.Win)
				.count();
		long allMonthDefeats = allMonthMatches.stream()
				.filter(m -> m.getMatchResult() != SplatoonMatchResult.Win)
				.count();

		long weaponPointsGain = allStreamMatches.stream()
				.filter(m -> m.getMode() == SplatoonMode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.mapToInt(SplatoonMatch::getTurfGain)
				.sum();
		long weaponWins = allStreamMatches.stream()
				.filter(m -> m.getMode() == SplatoonMode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.filter(m -> m.getMatchResult() == SplatoonMatchResult.Win)
				.count();
		long weaponDefeats = allStreamMatches.stream()
				.filter(m -> m.getMode() == SplatoonMode.Ranked)
				.filter(m -> m.getWeaponId() == lastMatchWeapon.getId())
				.filter(m -> m.getMatchResult() != SplatoonMatchResult.Win)
				.count();

		List<SplatoonMonthlyResult> allPeaks = monthlyResultRepository.findAll().stream()
				.filter(mr -> mr.getZonesPeak() != null || mr.getRainmakerPeak() != null || mr.getTowerPeak() != null || mr.getClamsPeak() != null)
				.collect(Collectors.toList());

		double zonesPeak = allPeaks.stream()
				.filter(mr -> mr.getZonesPeak() != null)
				.mapToDouble(SplatoonMonthlyResult::getZonesPeak)
				.max()
				.orElse(0.0);
		double rainmakerPeak = allPeaks.stream()
				.filter(mr -> mr.getRainmakerPeak() != null)
				.mapToDouble(SplatoonMonthlyResult::getRainmakerPeak)
				.max()
				.orElse(0.0);
		double towerPeak = allPeaks.stream()
				.filter(mr -> mr.getTowerPeak() != null)
				.mapToDouble(SplatoonMonthlyResult::getTowerPeak)
				.max()
				.orElse(0.0);
		double clamsPeak = allPeaks.stream()
				.filter(mr -> mr.getClamsPeak() != null)
				.mapToDouble(SplatoonMonthlyResult::getClamsPeak)
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
					.replace("{main-weapon-wins}", String.format("%d", lastMatchWeapon.getWins()))
					.replace("{main-weapon-defeats}", String.format("%d", lastMatchWeapon.getDefeats()))

					.replace("{main-weapon}", String.format("%s", lastMatchWeapon.getName()))
					.replace("{main-weapon-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchWeapon.getImage()))
					.replace("{sub-weapon-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchWeapon.getSubImage()))
					.replace("{special-weapon-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchWeapon.getSpecialImage()))

					.replace("{head-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchHead.getImage()))
					.replace("{head-main-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Head).get(0)))
					.replace("{head-sub-1-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Head).get(1)))

					.replace("{clothing-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchClothes.getImage()))
					.replace("{clothing-main-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Clothes).get(0)))
					.replace("{clothing-sub-1-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Clothes).get(1)))

					.replace("{shoes-image}", String.format("https://app.splatoon2.nintendo.net%s", lastMatchShoes.getImage()))
					.replace("{shoes-main-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Shoes).get(0)))
					.replace("{shoes-sub-1-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Shoes).get(1)))

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
							.filter(sm -> sm.getMode() == SplatoonMode.Ranked)
							.filter(sm -> sm.getRule() == lastMatch.getRule())
							.filter(sm -> sm.getMatchResult() == SplatoonMatchResult.Win)
							.count()))
					.replace("{game-rule-defeats-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == SplatoonMode.Ranked)
							.filter(sm -> sm.getRule() == lastMatch.getRule())
							.filter(sm -> sm.getMatchResult() != SplatoonMatchResult.Win)
							.count()))
					.replace("{ranked-wins-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == SplatoonMode.Ranked)
							.filter(sm -> sm.getMatchResult() == SplatoonMatchResult.Win)
							.count()))
					.replace("{ranked-defeats-current}", String.format("%d", allStreamMatches.stream()
							.filter(sm -> sm.getMode() == SplatoonMode.Ranked)
							.filter(sm -> sm.getMatchResult() != SplatoonMatchResult.Win)
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
					.replace("{stage-a-image}", String.format("https://app.splatoon2.nintendo.net%s", stageA.getImage()))

					.replace("{stage-b-wins}", String.format("%d", stageBWins))
					.replace("{stage-b-defeats}", String.format("%d", stageBDefeats))
					.replace("{stage-b-image}", String.format("https://app.splatoon2.nintendo.net%s", stageB.getImage()))

					.replace("{next-rotation-stage-a-image}", String.format("https://app.splatoon2.nintendo.net%s", nextStageA.getImage()))
					.replace("{next-rotation-stage-b-image}", String.format("https://app.splatoon2.nintendo.net%s", nextStageB.getImage()));

			if (perkImages.get(SplatoonGearType.Head).size() > 2) {
				currentHtml = currentHtml.replace("{head-sub-2-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Head).get(2)))
						.replace("{head-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{head-sub-2-image}", "")
						.replace("{head-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(SplatoonGearType.Head).size() > 3) {
				currentHtml = currentHtml.replace("{head-sub-3-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Head).get(3)))
						.replace("{head-sub-3-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{head-sub-3-image}", "")
						.replace("{head-sub-3-hidden}", "hidden");
			}

			if (perkImages.get(SplatoonGearType.Clothes).size() > 2) {
				currentHtml = currentHtml.replace("{clothing-sub-2-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Clothes).get(2)))
						.replace("{clothing-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{clothing-sub-2-image}", "")
						.replace("{clothing-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(SplatoonGearType.Clothes).size() > 3) {
				currentHtml = currentHtml.replace("{clothing-sub-3-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Clothes).get(3)))
						.replace("{clothing-sub-3-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{clothing-sub-3-image}", "")
						.replace("{clothing-sub-3-hidden}", "hidden");
			}

			if (perkImages.get(SplatoonGearType.Shoes).size() > 2) {
				currentHtml = currentHtml.replace("{shoes-sub-2-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Shoes).get(2)))
						.replace("{shoes-sub-2-hidden}", "");
			} else {
				currentHtml = currentHtml.replace("{shoes-sub-2-image}", "")
						.replace("{shoes-sub-2-hidden}", "hidden");
			}

			if (perkImages.get(SplatoonGearType.Shoes).size() > 3) {
				currentHtml = currentHtml.replace("{shoes-sub-3-image}", String.format("https://app.splatoon2.nintendo.net%s", perkImages.get(SplatoonGearType.Shoes).get(3)))
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
			return;
		}
	}

	private Double getPowerForRule(SplatoonMonthlyResult result, SplatoonRule rule) {
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

	private String getGameRuleString(SplatoonRule rule) {
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

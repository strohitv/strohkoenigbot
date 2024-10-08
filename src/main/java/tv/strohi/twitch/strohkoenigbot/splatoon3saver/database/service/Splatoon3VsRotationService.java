package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.RotationSchedulesResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class Splatoon3VsRotationService {
	private final LogSender logSender;

	private final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
	private final Instant challengeSlotExpansionDate = Instant.parse("2023-11-30T00:00:00Z");

	private final ObjectMapper mapper = new ObjectMapper();

	private final ImageService imageService;

	private final Splatoon3VsRotationRepository rotationRepository;
	private final Splatoon3VsRotationSlotRepository rotationSlotRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final Splatoon3VsRuleRepository ruleRepository;
	private final Splatoon3VsStageRepository stageRepository;
	private final Splatoon3VsEventRegulationRepository eventRegulationRepository;


	@Transactional
	public List<Splatoon3VsRotation> ensureRotationExists(Rotation rotation) {
		return getRotationMatchSetting(rotation).stream()
			.map(ms -> {
				if (ms.getVsStages() == null && ms.getVsRule() == null) {
					// rotation not active during splatfest
					return null;
				}

				var modeDistinction = ms.getBankaraMode() != null ? ms.getBankaraMode() : ms.getFestMode();

				var mode = modeRepository.findByApiTypenameAndApiModeDistinction(ms.get__typename(), modeDistinction)
					.orElseThrow();
				if ("PRIVATE".equalsIgnoreCase(mode.getApiMode())) {
					// no rotations in private battles
					return null;
				}

				var rule = ensureRuleExists(ms.getVsRule());

				var stages = Arrays.stream(ms.getVsStages()).map(this::ensureStageExists).collect(Collectors.toList());
				var stage1 = stages.stream().findFirst().orElse(null);
				var stage2 = stages.stream().skip(1).findFirst().orElse(null);

				return rotationRepository.findByModeAndStartTime(mode, rotation.getStartTimeAsInstant())
					.orElseGet(() -> {
						var newRotation = rotationRepository.save(
							Splatoon3VsRotation.builder()
								.startTime(rotation.getStartTimeAsInstant())
								.endTime(rotation.getEndTimeAsInstant())
								.mode(mode)
								.rule(rule)
								.stage1(stage1)
								.stage2(stage2)
								.slots(List.of())
								.eventRegulation(null)
								.shortenedJson(imageService.shortenJson(writeValueAsStringHiddenException(rotation)))
								.build());

						log.info("Created new rotation id: {}, start time: '{}', mode: '{}', rule: '{}'", newRotation.getId(), newRotation.getStartTime(), newRotation.getMode().getName(), newRotation.getRule().getName());

						return newRotation.toBuilder()
							.slots(List.of(rotationSlotRepository.save(Splatoon3VsRotationSlot.builder()
								.startTime(rotation.getStartTimeAsInstant())
								.endTime(rotation.getEndTimeAsInstant())
								.rotation(newRotation)
								.build())))
							.build();
					});
			})
			.collect(Collectors.toList());
	}


	@Transactional
	public Splatoon3VsRotation ensureRotationExists(RotationSchedulesResult.EventNode rotation) {
		var mode = modeRepository.findByApiTypenameAndApiModeDistinction(rotation.getLeagueMatchSetting().get__typename(), null)
			.orElseThrow();
		if ("PRIVATE".equalsIgnoreCase(mode.getApiMode())) {
			// no rotations in private battles
			return null;
		}

		var rule = ensureRuleExists(rotation.getLeagueMatchSetting().getVsRule());

		var stages = Arrays.stream(rotation.getLeagueMatchSetting().getVsStages()).map(this::ensureStageExists).collect(Collectors.toList());
		var stage1 = stages.stream().findFirst().orElse(null);
		var stage2 = stages.stream().skip(1).findFirst().orElse(null);

		return rotationRepository.findByModeAndStartTime(mode, rotation.getEarliestOccurrence())
			.orElseGet(() -> {
				var newRotation = rotationRepository.save(
					Splatoon3VsRotation.builder()
						.startTime(rotation.getEarliestOccurrence())
						.endTime(rotation.getLatestEnd())
						.mode(mode)
						.rule(rule)
						.stage1(stage1)
						.stage2(stage2)
						.slots(List.of())
						.eventRegulation(ensureEventRegulationExists(rotation.getLeagueMatchSetting().getLeagueMatchEvent()))
						.shortenedJson(imageService.shortenJson(writeValueAsStringHiddenException(rotation)))
						.build());

				var slots = Arrays.stream(rotation.getTimePeriods())
					.map(tp -> rotationSlotRepository.save(Splatoon3VsRotationSlot.builder()
						.startTime(tp.getStartTimeAsInstant())
						.endTime(tp.getEndTimeAsInstant())
						.rotation(newRotation)
						.build()))
					.collect(Collectors.toList());

				log.info("Created new event rotation id: {}, start time: '{}', mode: '{}', rule: '{}'", newRotation.getId(), newRotation.getStartTime(), newRotation.getMode().getName(), newRotation.getRule().getName());

				return newRotation.toBuilder()
					.slots(slots)
					.build();
			});
	}


	@Transactional
	public void ensureTricolorRotationsExist(RotationSchedulesResult.Fest currentFest) {
		if (currentFest.getTimetable() != null && currentFest.getTimetable().length > 0) {
			Arrays.stream(currentFest.getTimetable())
				.map(rotation -> Rotation.builder()
					.startTime(rotation.getStartTime())
					.endTime(rotation.getEndTime())
					.festMatchSettings(new RotationMatchSetting[]{
						RotationMatchSetting.builder()
							.vsStages(Arrays.stream(rotation.getFestMatchSettings()).findFirst().map(RotationMatchSetting::getVsStages).orElseThrow())
							.festMode("TRI_COLOR")
							.__typename("FestMatchSetting")
							.__isVsSetting("FestMatchSetting")
							.vsRule(VsRule.builder()
								.id("VnNSdWxlLTU=")
								.name("Tricolor Turf War")
								.rule("TRI_COLOR")
								.build())
							.build()
					})
					.build())
				.forEach(this::ensureRotationExists);
		} else if (currentFest.getTricolorStages() != null && currentFest.getTricolorStages().length > 0) {
			var slotList = new ArrayList<Instant>();
			slotList.add(currentFest.getMidTermTimeAsInstant());

			while (slotList.get(slotList.size() - 1).plus(2, ChronoUnit.HOURS).isBefore(currentFest.getEndTimeAsInstant())) {
				slotList.add(slotList.get(slotList.size() - 1).plus(2, ChronoUnit.HOURS));
			}

			slotList.stream()
				.map(slot -> Rotation.builder()
					.startTime(formatter.format(slot.atZone(ZoneOffset.UTC)))
					.endTime(formatter.format(slot.plus(2, ChronoUnit.HOURS).atZone(ZoneOffset.UTC)))
					.festMatchSettings(new RotationMatchSetting[] {
						RotationMatchSetting.builder()
							.vsStages(new VsStage[]{Arrays.stream(currentFest.getTricolorStages()).findFirst().orElseThrow()})
							.festMode("TRI_COLOR")
							.__typename("FestMatchSetting")
							.__isVsSetting("FestMatchSetting")
							.vsRule(VsRule.builder()
								.id("VnNSdWxlLTU=")
								.name("Tricolor Turf War")
								.rule("TRI_COLOR")
								.build())
							.build()
					})
					.build())
				.forEach(this::ensureRotationExists);
		}
	}


	@Transactional
	public Splatoon3VsRule ensureRuleExists(VsRule rule) {
		return ruleRepository.findByApiId(rule.getId())
			.orElseGet(() -> ruleRepository.save(Splatoon3VsRule.builder()
				.apiId(rule.getId())
				.name(rule.getName())
				.apiRule(rule.getRule())
				.build()
			));
	}


	@Transactional
	public Splatoon3VsStage ensureStageExists(VsStage stage) {
		var dbStage = stageRepository.findByApiId(stage.getId())
			.orElseGet(() -> stageRepository.save(Splatoon3VsStage.builder()
				.apiId(stage.getId())
				.name(stage.getName())
				.image(stage.getOriginalImage() != null
					? imageService.ensureExists(stage.getOriginalImage().getUrl())
					: imageService.ensureExists(stage.getImage().getUrl()))
				.build()
			));

		var bestUrl = getBestUrl(dbStage.getImage() != null ? dbStage.getImage().getUrl() : null,
			stage.getImage() != null ? stage.getImage().getUrl() : null,
			stage.getOriginalImage() != null ? stage.getOriginalImage().getUrl() : null);

		if (bestUrl.isPresent()
			&& (dbStage.getImage() == null || imageService.isFailed(dbStage.getImage()) || !dbStage.getImage().getUrl().equals(bestUrl.get()))) {
			dbStage = stageRepository.save(dbStage.toBuilder()
				.image(imageService.ensureExists(bestUrl.get()))
				.build());

			logSender.sendLogs(log, String.format("Set image for vs stage with id `%d` to `%s`", dbStage.getId(), bestUrl.get()));
		}

		return dbStage;
	}

	private Optional<String> getBestUrl(String... urls) {
		return Arrays.stream(urls)
			.filter(Objects::nonNull)
			.max((a, b) -> Integer.compare(getUrlValue(a), getUrlValue(b)));
	}

	private int getUrlValue(String url) {
		if (url.contains("icon/high_resolution")) {
			return 100;
		}

		if (url.contains("icon/low_resolution")) {
			return 50;
		}

		return 0;
	}


	@Transactional
	public Splatoon3VsEventRegulation ensureEventRegulationExists(RotationSchedulesResult.LeagueMatchEvent event) {
		return eventRegulationRepository.findByApiId(event.getId())
			.map(found -> eventRegulationRepository.save(found.toBuilder()
				.apiId(event.getId())
				.apiLeagueMatchEventId(event.getLeagueMatchEventId())
				.name(event.getName())
				.description(event.getDesc())
				.regulation(event.getRegulation())
				.regulationUrl(event.getRegulationUrl())
				.build()))
			.orElseGet(() -> eventRegulationRepository.save(Splatoon3VsEventRegulation.builder()
				.apiId(event.getId())
				.apiLeagueMatchEventId(event.getLeagueMatchEventId())
				.name(event.getName())
				.description(event.getDesc())
				.regulation(event.getRegulation())
				.regulationUrl(event.getRegulationUrl())
				.build()
			));
	}


	@Transactional
	public Splatoon3VsRotation ensureDummyRotationExists(Splatoon3VsMode mode, Splatoon3VsRule rule, Splatoon3VsStage stage, Instant playedTime, IdAndNameAndDescription eventRegulation) {
		if ("PRIVATE".equalsIgnoreCase(mode.getApiMode())) {
			// no rotations in private battles
			return null;
		}

		var rotationStartTime = getRotationStartTime(playedTime, mode);
		var rotationEndTime = getRotationEndTime(playedTime, mode);

		var rotationSlots = getRotationSlots(rotationStartTime, mode);

		var rotation = rotationRepository.findByModeAndStartTime(mode, rotationStartTime)
			.orElseGet(() -> {
				var newRotation = rotationRepository.save(Splatoon3VsRotation.builder()
					.startTime(rotationStartTime)
					.endTime(rotationEndTime)
					.mode(mode)
					.rule(rule)
					.stage1(stage)
					.eventRegulation(createDummyEventRegulationIfNecessary(eventRegulation))
					.shortenedJson("")
					.build());

				log.info("Created new dummy rotation id: {}, start time: '{}', mode: '{}', rule: '{}'", newRotation.getId(), newRotation.getStartTime(), newRotation.getMode().getName(), newRotation.getRule().getName());

				return newRotation;
			});

		if (rotation.getSlots() == null || rotation.getSlots().size() == 0) {
			var list = new ArrayList<Splatoon3VsRotationSlot>();

			for (var slot : rotationSlots) {
				list.add(rotationSlotRepository.save(slot.toBuilder().rotation(rotation).build()));
			}

			rotation = rotationRepository.save(rotation.toBuilder().slots(list).build());
		}

		if (rotation.getStage2() == null && !Objects.equals(rotation.getStage1().getId(), stage.getId())) {
			// dummy rotation, stage not included yet
			rotation = rotationRepository.save(rotation.toBuilder().stage2(stage).build());
		}

		return rotation;
	}


	@Transactional
	public Splatoon3VsEventRegulation createDummyEventRegulationIfNecessary(IdAndNameAndDescription eventRegulation) {
		if (eventRegulation == null) return null;

		return eventRegulationRepository.findByApiId(eventRegulation.getId())
			.orElseGet(() ->
				eventRegulationRepository.save(Splatoon3VsEventRegulation.builder()
					.apiId(eventRegulation.getId())
					.name(eventRegulation.getName())
					.description(eventRegulation.getDesc())
					.build()));
	}

	private String writeValueAsStringHiddenException(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			// never happens
			throw new RuntimeException(e);
		}
	}

	private List<RotationMatchSetting> getRotationMatchSetting(Rotation rotation) {
		if (rotation.getRegularMatchSetting() != null) {
			return List.of(rotation.getRegularMatchSetting());
		} else if (rotation.getBankaraMatchSettings() != null) {
			return Arrays.stream(rotation.getBankaraMatchSettings()).collect(Collectors.toList());
		} else if (rotation.getXMatchSetting() != null) {
			return List.of(rotation.getXMatchSetting());
		} else if (rotation.getFestMatchSettings() != null) {
			return Arrays.stream(rotation.getFestMatchSettings()).collect(Collectors.toList());
		}

		return List.of();
	}

	private Instant getRotationStartTime(Instant playedTime, Splatoon3VsMode mode) {
		if (mode.getApiMode().equals("LEAGUE")) {
			// challenge
			var hour = playedTime.atZone(ZoneOffset.UTC).getHour();

			if (challengeSlotExpansionDate.isBefore(playedTime)
				|| (hour >= 2 && hour < 4)
				|| (hour >= 10 && hour < 12)
				|| (hour >= 18 && hour < 20)) {
				// challenge with 6 slots OR challenge with 3 slots & earlier timeslots
				return playedTime.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
					.withHour(2)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			} else {
				// later challenge timeslots
				return playedTime.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
					.withHour(4)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			}
		} else {
			// regular rotations changing every 2 hours
			return playedTime.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
				.withHour(playedTime.atZone(ZoneOffset.UTC).getHour() / 2 * 2)
				.withMinute(0)
				.withSecond(0)
				.withNano(0)
				.toInstant();
		}
	}

	private Instant getRotationEndTime(Instant playedTime, Splatoon3VsMode mode) {
		if (mode.getApiMode().equals("LEAGUE")) {
			// challenge
			var hour = playedTime.atZone(ZoneOffset.UTC).getHour();

			if (challengeSlotExpansionDate.isAfter(playedTime)) {
				// challenge with 6 slots
				return playedTime.atZone(ZoneOffset.UTC)
					.truncatedTo(ChronoUnit.DAYS)
					.plus(1, ChronoUnit.DAYS)
					.withHour(0)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			} else if ((hour >= 2 && hour < 4)
				|| (hour >= 10 && hour < 12)
				|| (hour >= 18 && hour < 20)) {
				// challenge with 3 slots & earlier timeslots
				return playedTime.atZone(ZoneOffset.UTC)
					.truncatedTo(ChronoUnit.DAYS)
					.withHour(20)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			} else {
				// challenge with 3 slots & later timeslots
				return playedTime.atZone(ZoneOffset.UTC)
					.truncatedTo(ChronoUnit.DAYS)
					.withHour(22)
					.withMinute(0)
					.withSecond(0)
					.withNano(0)
					.toInstant();
			}
		} else {
			// regular rotations changing every 2 hours
			return playedTime.atZone(ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.DAYS)
				.withHour(playedTime.atZone(ZoneOffset.UTC).getHour() / 2 * 2)
				.plus(2, ChronoUnit.HOURS)
				.withMinute(0)
				.withSecond(0)
				.withNano(0)
				.toInstant();
		}
	}

	private List<Splatoon3VsRotationSlot> getRotationSlots(Instant rotationStartTime, Splatoon3VsMode mode) {
		var hour = rotationStartTime.atZone(ZoneOffset.UTC).getHour();

		if (mode.getApiMode().equals("LEAGUE")) {
			// challenge

			if (challengeSlotExpansionDate.isAfter(rotationStartTime)) {
				// challenge with 6 slots
				return List.of(
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 2))
						.endTime(getInstantAtHour(rotationStartTime, 4))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 6))
						.endTime(getInstantAtHour(rotationStartTime, 8))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 10))
						.endTime(getInstantAtHour(rotationStartTime, 12))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 14))
						.endTime(getInstantAtHour(rotationStartTime, 16))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 18))
						.endTime(getInstantAtHour(rotationStartTime, 20))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 22))
						.endTime(getInstantAtHour(rotationStartTime.plus(1, ChronoUnit.DAYS), 0))
						.build());
			} else if (hour == 2
				|| hour == 10
				|| hour == 18) {
				// challenge with 3 slots & earlier timeslots
				return List.of(
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 2))
						.endTime(getInstantAtHour(rotationStartTime, 4))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 10))
						.endTime(getInstantAtHour(rotationStartTime, 12))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 18))
						.endTime(getInstantAtHour(rotationStartTime, 20))
						.build());
			} else {
				// challenge with 3 slots & later timeslots
				return List.of(
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 4))
						.endTime(getInstantAtHour(rotationStartTime, 6))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 12))
						.endTime(getInstantAtHour(rotationStartTime, 14))
						.build(),
					Splatoon3VsRotationSlot.builder()
						.startTime(getInstantAtHour(rotationStartTime, 20))
						.endTime(getInstantAtHour(rotationStartTime, 22))
						.build());
			}
		} else {
			// regular rotations changing every 2 hours
			return List.of(
				Splatoon3VsRotationSlot.builder()
					.startTime(rotationStartTime)
					.endTime(getInstantAtHour(rotationStartTime, rotationStartTime.atZone(ZoneOffset.UTC).getHour())
						.plus(2, ChronoUnit.HOURS))
					.build());
		}
	}

	private Instant getInstantAtHour(Instant base, int hour) {
		return base.atZone(ZoneOffset.UTC)
			.truncatedTo(ChronoUnit.DAYS)
			.withHour(hour)
			.withMinute(0)
			.withSecond(0)
			.withNano(0)
			.toInstant();
	}
}

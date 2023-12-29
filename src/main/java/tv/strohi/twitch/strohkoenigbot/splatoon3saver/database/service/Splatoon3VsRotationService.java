package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.RotationSchedulesResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Rotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.RotationMatchSetting;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.VsRule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.VsStage;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Splatoon3VsRotationService {
	private final ObjectMapper mapper = new ObjectMapper();

	private final ImageService imageService;

	private final Splatoon3VsRotationRepository rotationRepository;
	private final Splatoon3VsRotationSlotRepository rotationSlotRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final Splatoon3VsRuleRepository ruleRepository;
	private final Splatoon3VsStageRepository stageRepository;
	private final Splatoon3VsEventRegulationRepository eventRegulationRepository;

	public List<Splatoon3VsRotation> ensureRotationExists(Rotation rotation) {
		return getRotationMatchSetting(rotation).stream()
			.map(ms -> {
				var mode = modeRepository.findByApiTypenameAndApiBankaraMode(ms.get__typename(), ms.getBankaraMode()).orElseThrow();
				var rule = ensureRuleExists(ms.getVsRule());

				var stages = Arrays.stream(ms.getVsStages()).map(this::ensureStageExists).collect(Collectors.toList());
				var stage1 = stages.stream().findFirst().orElse(null);
				var stage2 = stages.stream().skip(1).findFirst().orElse(null);

				return rotationRepository.findByModeAndStartTime(mode, rotation.getStartTimeAsInstant())
					.orElseGet(() -> {
						var insertedRotation = rotationRepository.save(
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

						return insertedRotation.toBuilder()
							.slots(List.of(rotationSlotRepository.save(Splatoon3VsRotationSlot.builder()
								.startTime(rotation.getStartTimeAsInstant())
								.endTime(rotation.getEndTimeAsInstant())
								.rotation(insertedRotation)
								.build())))
							.build();
					});
			})
			.collect(Collectors.toList());
	}

	public Splatoon3VsRotation ensureRotationExists(RotationSchedulesResult.EventNode rotation) {
		var mode = modeRepository.findByApiTypenameAndApiBankaraMode(rotation.getLeagueMatchSetting().get__typename(), null).orElseThrow();
		var rule = ensureRuleExists(rotation.getLeagueMatchSetting().getVsRule());

		var stages = Arrays.stream(rotation.getLeagueMatchSetting().getVsStages()).map(this::ensureStageExists).collect(Collectors.toList());
		var stage1 = stages.stream().findFirst().orElse(null);
		var stage2 = stages.stream().skip(1).findFirst().orElse(null);

		return rotationRepository.findByModeAndStartTime(mode, rotation.getEarliestOccurrence())
			.orElseGet(() -> {
				var insertedRotation = rotationRepository.save(
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
						.rotation(insertedRotation)
						.build()))
					.collect(Collectors.toList());

				return insertedRotation.toBuilder()
					.slots(slots)
					.build();
			});
	}

	public Splatoon3VsRule ensureRuleExists(VsRule rule) {
		return ruleRepository.findByApiId(rule.getId())
			.orElseGet(() -> ruleRepository.save(Splatoon3VsRule.builder()
				.apiId(rule.getId())
				.name(rule.getName())
				.apiRule(rule.getRule())
				.build()
			));
	}

	public Splatoon3VsStage ensureStageExists(VsStage stage) {
		return stageRepository.findByApiId(stage.getId())
			.orElseGet(() -> stageRepository.save(Splatoon3VsStage.builder()
				.apiId(stage.getId())
				.name(stage.getName())
				.image(imageService.ensureExists(stage.getImage().getUrl()))
				.build()
			));
	}

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
}

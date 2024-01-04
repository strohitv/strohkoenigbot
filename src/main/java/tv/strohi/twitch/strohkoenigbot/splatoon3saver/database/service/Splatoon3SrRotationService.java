package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrBoss;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrStage;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class Splatoon3SrRotationService {
	private final Instant horrorborosIntroductionDate = Instant.parse("2023-03-04T00:00:00Z");
	private final ObjectMapper mapper = new ObjectMapper();

	private final ImageService imageService;

	private final Splatoon3SrRotationRepository rotationRepository;
	private final Splatoon3SrModeRepository modeRepository;
	private final Splatoon3SrBossRepository bossRepository;
	private final Splatoon3SrStageRepository stageRepository;
	private final Splatoon3SrWeaponRepository weaponRepository;


	@Transactional
	public Splatoon3SrRotation ensureRotationExists(CoopRotation rotation) {
		var mode = modeRepository.findByApiTypename(rotation.getSetting().get__typename())
			.orElseThrow();

		var weapons = Arrays.stream(rotation.getSetting().getWeapons()).collect(Collectors.toList());

		return rotationRepository.save(
			rotationRepository.findByModeAndStartTime(mode, rotation.getStartTimeAsInstant())
				.orElseGet(() -> {
					var newRotation = rotationRepository.save(
						Splatoon3SrRotation.builder()
							.mode(mode)
							.startTime(rotation.getStartTimeAsInstant())
							.endTime(rotation.getEndTimeAsInstant())
							.stage(ensureStageExists(rotation.getSetting().getCoopStage()))
							.weapon1(ensureWeaponExists(weapons.get(0)))
							.weapon2(ensureWeaponExists(weapons.get(1)))
							.weapon3(ensureWeaponExists(weapons.get(2)))
							.weapon4(ensureWeaponExists(weapons.get(3)))
							.boss(ensureBossExists(rotation.getSetting().getBoss()))
							.shortenedJson(imageService.shortenJson(writeValueAsStringHiddenException(rotation)))
							.build());

					log.info("Created new rotation id: {}, start time: '{}', mode: '{}'", newRotation.getId(), newRotation.getStartTime(), newRotation.getMode().getName());

					return newRotation;
				})
				.toBuilder()
				.stage(ensureStageExists(rotation.getSetting().getCoopStage()))
				.weapon1(ensureWeaponExists(weapons.get(0)))
				.weapon2(ensureWeaponExists(weapons.get(1)))
				.weapon3(ensureWeaponExists(weapons.get(2)))
				.weapon4(ensureWeaponExists(weapons.get(3)))
				.build()
		);
	}

	@Transactional
	public Splatoon3SrRotation ensureDummyRotationExists(BattleResults.HistoryGroupsNodes rotation) {
		var mode = modeRepository.findByApiModeAndApiRule(rotation.getMode(), rotation.getRule())
			.orElse(null);

		if (mode == null) return null;

		var weapons = Arrays.stream(rotation.getHistoryDetails().getNodes())
			.map(BattleResults.HistoryGroupMatch::getWeapons)
			.map(List::of)
			.findAny()
			.orElse(List.of())
			.stream()
			.map(w -> NameAndImage.builder()
				.name(w.getName())
				.image(w.getImage())
				.build())
			.collect(Collectors.toList());

		if (weapons.size() < 4) return null;

		var stage = Arrays.stream(rotation.getHistoryDetails().getNodes())
			.findAny()
			.map(BattleResults.HistoryGroupMatch::getCoopStage)
			.orElse(null);

		if (stage == null) return null;

		var boss = Arrays.stream(rotation.getHistoryDetails().getNodes())
			.map(BattleResults.HistoryGroupMatch::getBossResult)
			.filter(Objects::nonNull)
			.findAny()
			.map(BossResult::getBoss)
			.orElse(null);

		return rotationRepository.save(
			rotationRepository.findByModeAndStartTime(mode, rotation.getStartTimeAsInstant())
				.orElseGet(() -> {
					var newRotation = rotationRepository.save(
						Splatoon3SrRotation.builder()
							.mode(mode)
							.startTime(rotation.getStartTimeAsInstant())
							.endTime(rotation.getEndTimeAsInstant())
							.stage(ensureStageExists(stage))
							.weapon1(ensureWeaponExists(weapons.get(0)))
							.weapon2(ensureWeaponExists(weapons.get(1)))
							.weapon3(ensureWeaponExists(weapons.get(2)))
							.weapon4(ensureWeaponExists(weapons.get(3)))
							.boss(tryChooseBoss(boss, rotation.getStartTimeAsInstant()))
							.shortenedJson(imageService.shortenJson(writeValueAsStringHiddenException(rotation)))
							.build());

					log.info("Created new dummy rotation id: {}, start time: '{}', mode: '{}'", newRotation.getId(), newRotation.getStartTime(), newRotation.getMode().getName());

					return newRotation;
				})
				.toBuilder()
				.stage(ensureStageExists(stage))
				.weapon1(ensureWeaponExists(weapons.get(0)))
				.weapon2(ensureWeaponExists(weapons.get(1)))
				.weapon3(ensureWeaponExists(weapons.get(2)))
				.weapon4(ensureWeaponExists(weapons.get(3)))
				.build()
		);
	}

	@Transactional
	public Splatoon3SrWeapon ensureWeaponExists(NameAndImage weapon) {
		return weaponRepository.findByName(weapon.getName())
			.orElseGet(() -> weaponRepository.save(Splatoon3SrWeapon.builder()
				.name(weapon.getName())
				.image(imageService.ensureExists(weapon.getImage().getUrl()))
				.build()
			));
	}


	@Transactional
	public Splatoon3SrStage ensureStageExists(CoopStage coopStage) {
		var stage = stageRepository.findByApiId(coopStage.getId())
			.orElseGet(() -> stageRepository.save(Splatoon3SrStage.builder()
				.apiId(coopStage.getId())
				.name(coopStage.getName())
				.image(coopStage.getImage() != null
					? imageService.ensureExists(coopStage.getImage().getUrl())
					: null)
				.thumbnailImage(coopStage.getThumbnailImage() != null
					? imageService.ensureExists(coopStage.getThumbnailImage().getUrl())
					: null)
				.build()
			));

		if ((stage.getImage() == null && coopStage.getImage() != null)
			|| (stage.getThumbnailImage() == null && coopStage.getThumbnailImage() != null)) {

			stage = stageRepository.save(stage.toBuilder()
				.image(coopStage.getImage() != null
					? imageService.ensureExists(coopStage.getImage().getUrl())
					: null)
				.thumbnailImage(coopStage.getThumbnailImage() != null
					? imageService.ensureExists(coopStage.getThumbnailImage().getUrl())
					: null)
				.build());
		}

		return stage;
	}


	@Transactional
	public Splatoon3SrBoss ensureBossExists(CoopBoss boss) {
		var dbBoss = bossRepository.findByName(boss.getName())
			.orElseGet(() -> bossRepository.save(Splatoon3SrBoss.builder()
				.apiId(boss.getId())
				.name(boss.getName())
				.enemyId(boss.getCoopEnemyId())
				.image(boss.getImage() != null ? imageService.ensureExists(boss.getImage().getUrl()) : null)
				.build()
			));

		if (dbBoss.getEnemyId() == null && boss.getCoopEnemyId() != null) {
			dbBoss = bossRepository.save(dbBoss.toBuilder()
				.enemyId(boss.getCoopEnemyId())
				.build());
		}

		if (dbBoss.getImage() == null && boss.getImage() != null) {
			dbBoss = bossRepository.save(dbBoss.toBuilder()
				.image(imageService.ensureExists(boss.getImage().getUrl()))
				.build());
		}

		return dbBoss;
	}

	private Splatoon3SrBoss tryChooseBoss(CoopBoss boss, Instant startTime) {
		if (boss != null) return ensureBossExists(boss);

		if (startTime.isBefore(horrorborosIntroductionDate)) {
			// only cohozuna did exist befor March 4th 2023
			return bossRepository
				.findByName("Cohozuna")
				.orElse(null);
		}

		return null;
	}

	private String writeValueAsStringHiddenException(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			// never happens
			throw new RuntimeException(e);
		}
	}
}

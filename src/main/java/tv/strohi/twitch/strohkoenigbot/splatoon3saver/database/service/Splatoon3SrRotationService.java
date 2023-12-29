package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrBoss;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrStage;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopRotation;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopStage;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.IdAndName;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.NameAndImage;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Splatoon3SrRotationService {
	private final ObjectMapper mapper = new ObjectMapper();

	private final ImageService imageService;

	private final Splatoon3SrRotationRepository rotationRepository;
	private final Splatoon3SrModeRepository modeRepository;
	private final Splatoon3SrBossRepository bossRepository;
	private final Splatoon3SrStageRepository stageRepository;
	private final Splatoon3SrWeaponRepository weaponRepository;

	public Splatoon3SrRotation ensureRotationExists(CoopRotation rotation) {
		var mode = modeRepository.findByApiTypename(rotation.getSetting().get__typename())
			.orElseThrow();

		var weapons = Arrays.stream(rotation.getSetting().getWeapons()).collect(Collectors.toList());

		return rotationRepository.save(
			rotationRepository.findByModeAndStartTime(mode, rotation.getStartTimeAsInstant())
				.orElseGet(() -> rotationRepository.save(
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
						.build()))
				.toBuilder()
				.stage(ensureStageExists(rotation.getSetting().getCoopStage()))
				.weapon1(ensureWeaponExists(weapons.get(0)))
				.weapon2(ensureWeaponExists(weapons.get(1)))
				.weapon3(ensureWeaponExists(weapons.get(2)))
				.weapon4(ensureWeaponExists(weapons.get(3)))
				.build()
		);
	}

	public Splatoon3SrWeapon ensureWeaponExists(NameAndImage weapon) {
		return weaponRepository.findByName(weapon.getName())
			.orElseGet(() -> weaponRepository.save(Splatoon3SrWeapon.builder()
				.name(weapon.getName())
				.image(imageService.ensureExists(weapon.getImage().getUrl()))
				.build()
			));
	}

	public Splatoon3SrStage ensureStageExists(CoopStage coopStage) {
		return stageRepository.findByApiId(coopStage.getId())
			.orElseGet(() -> stageRepository.save(Splatoon3SrStage.builder()
				.apiId(coopStage.getId())
				.name(coopStage.getName())
				.image(imageService.ensureExists(coopStage.getImage().getUrl()))
				.shortenedThumbnailImage(imageService.ensureExists(coopStage.getThumbnailImage().getUrl()))
				.build()
			));
	}

	public Splatoon3SrBoss ensureBossExists(IdAndName boss) {
		return bossRepository.findByName(boss.getName())
			.orElseGet(() -> bossRepository.save(Splatoon3SrBoss.builder()
				.apiId(boss.getId())
				.name(boss.getName())
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
}

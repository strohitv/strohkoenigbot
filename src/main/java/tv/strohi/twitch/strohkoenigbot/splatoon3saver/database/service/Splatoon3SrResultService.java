package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import javax.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class Splatoon3SrResultService {
	// todo add indices based on the repository searches to make the algorithm fast
	private final LogSender logSender;

	private final ImageService imageService;
	private final Splatoon3GeneralService generalService;
	private final Splatoon3SrRotationService rotationService;

	private final Splatoon3SrModeRepository modeRepository;
	private final Splatoon3SrResultRepository resultRepository;
	private final Splatoon3SrRotationRepository rotationRepository;
	private final Splatoon3SrGradeRepository gradeRepository;
	private final Splatoon3SrBossResultRepository bossResultRepository;
	private final Splatoon3SrEnemyRepository enemyRepository;
	private final Splatoon3SrResultEnemyRepository resultEnemyRepository;
	private final Splatoon3SrResultPlayerRepository resultPlayerRepository;
	private final Splatoon3SrResultWavePlayerWeaponRepository resultWavePlayerWeaponRepository;
	private final Splatoon3SrUniformRepository uniformRepository;
	private final Splatoon3SrResultWaveRepository resultWaveRepository;
	private final Splatoon3SrResultWaveUsedSpecialWeaponRepository resultWaveUsedSpecialWeaponRepository;
	private final Splatoon3SrEventWaveRepository eventWaveRepository;
	private final Splatoon3SrSpecialWeaponRepository specialWeaponRepository;

	public Splatoon3SrResult ensureResultExists(CoopHistoryDetail result, String json) {
		var possibleModes = modeRepository.findByApiRule(result.getRule());
		Splatoon3SrMode mode = chooseMode(possibleModes, result);

		var rotation = !mode.getApiMode().contains("PRIVATE")
			? rotationRepository.findByModeAndStartTimeBeforeAndEndTimeAfter(mode, result.getPlayedTimeAsInstant(), result.getPlayedTimeAsInstant()).orElseThrow()
			: null;

		var boss = result.tryGetBoss() != null ? rotationService.ensureBossExists(result.tryGetBoss()) : null;

		if (boss != null && rotation != null && rotation.getBoss() == null) {
			rotationRepository.save(rotation.toBuilder().boss(boss).build());
		}

		var dbResult = resultRepository.findByApiId(result.getId())
			.orElseGet(() -> {
				var newResult = resultRepository.save(
					Splatoon3SrResult.builder()
						.apiId(result.getId())
						.mode(mode)
						.stage(rotationService.ensureStageExists(result.getCoopStage()))
						.playedTime(result.getPlayedTimeAsInstant())
						.rotation(rotation)
						.smellMeter(result.getSmellMeter())
						.boss(boss)
						.successful(result.getWaveResults().stream().allMatch(wr -> getSuccessful(result.getAllBossResults(), wr)))
						.dangerRate(result.getDangerRate())
						.earnedBronzeScales(result.getScale() != null ? result.getScale().getBronze() : null)
						.earnedSilverScales(result.getScale() != null ? result.getScale().getSilver() : null)
						.earnedGoldScales(result.getScale() != null ? result.getScale().getGold() : null)
						.jobBonus(result.getJobBonus())
						.jobPoint(result.getJobPoint())
						.jobRate(result.getJobRate())
						.jobScore(result.getJobScore())
						.scenarioCode(result.getScenarioCode())
						.afterGrade(ensureGradeExists(result.getAfterGrade()))
						.afterGradePoint(result.getAfterGradePoint())
						.shortenedJson(imageService.shortenJson(json))
						.build()
				);

				if ((newResult.getBossResults() == null || newResult.getBossResults().isEmpty())
					&& !result.getAllBossResults().isEmpty()) {
					var added = result.getAllBossResults().stream()
						.map(r -> bossResultRepository.save(Splatoon3SrBossResult.builder()
							.result(newResult)
							.boss(rotationService.ensureBossExists(r.getBoss()))
							.defeated(r.getHasDefeatBoss())
							.build()))
						.collect(Collectors.toList());

					log.info("Created {} new boss results for sr result id: {}", added.size(), newResult.getId());
				}

				log.info("Created new sr result id: {}, played time: '{}', mode: '{}', stage: {}, rank: {} {}", newResult.getId(), newResult.getPlayedTime(), newResult.getMode().getName(), newResult.getStage().getName(), newResult.getAfterGrade(), newResult.getAfterGradePoint());

				return newResult;
			});

		var allPlayerResults = Stream.concat(Stream.of(result.getMyResult()), Stream.of(result.getMemberResults().toArray(new CoopResult[0]))).collect(Collectors.toList());

		ensureResultPlayersExist(dbResult, allPlayerResults);
		var resultWaves = ensureWaveExists(dbResult, result.getWaveResults());
		ensureResultWavePlayerWeaponsExist(resultWaves, allPlayerResults);
		ensureResultEnemiesExist(dbResult, result.getEnemyResults());

		return dbResult;
	}

	private Splatoon3SrMode chooseMode(List<Splatoon3SrMode> possibleModes, CoopHistoryDetail result) {
		var mode = possibleModes.stream().findFirst().orElseThrow();

		if ("REGULAR".equalsIgnoreCase(result.getRule())) {
			if (result.getScenarioCode() != null) {
				// scenario private shift
				mode = possibleModes.stream().filter(m -> m.getApiMode().equalsIgnoreCase("PRIVATE_SCENARIO")).findFirst().orElseThrow();
			} else if (result.getAfterGrade() == null && result.getAfterGradePoint() == null) {
				// regular private shift
				mode = possibleModes.stream().filter(m -> m.getApiMode().equalsIgnoreCase("PRIVATE_CUSTOM")).findFirst().orElseThrow();
			} else {
				// regular online shift
				mode = possibleModes.stream().filter(m -> m.getApiMode().equalsIgnoreCase("REGULAR")).findFirst().orElseThrow();
			}
		}

		return mode;
	}

	public List<Splatoon3SrResultEnemy> ensureResultEnemiesExist(Splatoon3SrResult dbResult, List<EnemyResults> enemyResults) {
		return enemyResults.stream()
			.map(er -> {
				var enemy = ensureEnemyExists(er.getEnemy());

				return resultEnemyRepository.findByResultIdAndEnemyId(dbResult.getId(), enemy.getId())
					.orElseGet(() ->
						resultEnemyRepository.save(Splatoon3SrResultEnemy.builder()
							.resultId(dbResult.getId())
							.enemyId(enemy.getId())
							.spawnCount(er.getPopCount())
							.ownDestroyCount(er.getDefeatCount())
							.teamDestroyCount(er.getTeamDefeatCount())
							.build())
					);
			})
			.collect(Collectors.toList());
	}

	public Splatoon3SrEnemy ensureEnemyExists(IdAndNameAndImage enemy) {
		var dbEnemy = enemyRepository.findByApiId(enemy.getId())
			.orElseGet(() ->
				enemyRepository.save(
					Splatoon3SrEnemy.builder()
						.apiId(enemy.getId())
						.name(enemy.getName())
						.image(imageService.ensureExists(enemy.getImage().getUrl()))
						.build()
				));

		if (imageService.isFailed(dbEnemy.getImage())
			&& !dbEnemy.getImage().getUrl().equals(enemy.getImage().getUrl())) {

			dbEnemy = enemyRepository.save(dbEnemy.toBuilder()
				.image(imageService.ensureExists(enemy.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for sr enemy with id `%d` to `%s`", dbEnemy.getId(), enemy.getImage().getUrl()));
		}

		return dbEnemy;
	}

	public List<Splatoon3SrResultWavePlayerWeapon> ensureResultWavePlayerWeaponsExist(List<Splatoon3SrResultWave> resultWaves, List<CoopResult> playerResults) {
		var orderedResultWaves = resultWaves.stream().sorted(Comparator.comparing(Splatoon3SrResultWave::getWaveNumber)).collect(Collectors.toList());

		return playerResults.stream()
			.flatMap(pr -> {
				var res = new ArrayList<Splatoon3SrResultWavePlayerWeapon>();

				var player = generalService.ensurePlayerExists(pr.getPlayer().getId());
				int index = 0;
				for (var prw : pr.getWeapons()) {
					var weapon = rotationService.ensureWeaponExists(prw);
					var wave = orderedResultWaves.get(index);

					res.add(resultWavePlayerWeaponRepository.findByResultIdAndWaveNumberAndPlayerIdAndWeaponId(wave.getResultId(), wave.getWaveNumber(), player.getId(), weapon.getId())
						.orElseGet(() -> resultWavePlayerWeaponRepository.save(Splatoon3SrResultWavePlayerWeapon.builder()
							.resultId(wave.getResultId())
							.waveNumber(wave.getWaveNumber())
							.playerId(player.getId())
							.weaponId(weapon.getId())
							.build())));

					index++;
				}

				return res.stream();
			})
			.collect(Collectors.toList());
	}

	public List<Splatoon3SrResultPlayer> ensureResultPlayersExist(Splatoon3SrResult dbResult, List<CoopResult> playerResults) {
		return playerResults.stream()
			.map(
				pr -> {
					var player = generalService.ensurePlayerExists(pr.getPlayer().getId());

					var badgeLeft = pr.getPlayer().getNameplate().getBadges().get(0) != null
						? generalService.ensureBadgeExists(pr.getPlayer().getNameplate().getBadges().get(0), player.isMyself())
						: null;

					var badgeMiddle = pr.getPlayer().getNameplate().getBadges().get(1) != null
						? generalService.ensureBadgeExists(pr.getPlayer().getNameplate().getBadges().get(1), player.isMyself())
						: null;

					var badgeRight = pr.getPlayer().getNameplate().getBadges().get(2) != null
						? generalService.ensureBadgeExists(pr.getPlayer().getNameplate().getBadges().get(2), player.isMyself())
						: null;

					return resultPlayerRepository.findByResultIdAndPlayerId(dbResult.getId(), player.getId())
						.orElseGet(() ->
							resultPlayerRepository.save(
								Splatoon3SrResultPlayer.builder()
									.resultId(dbResult.getId())
									.playerId(player.getId())
									.nameplate(generalService.ensureNameplateExists(pr.getPlayer().getNameplate(), player.isMyself()))
									.badgeLeft(badgeLeft)
									.badgeMiddle(badgeMiddle)
									.badgeRight(badgeRight)
									.name(pr.getPlayer().getName())
									.nameId(pr.getPlayer().getNameId())
									.isMyself(player.isMyself())
									.Species(pr.getPlayer().getSpecies())
									.specialWeapon(pr.getSpecialWeapon() != null ? ensureSpecialWeaponExists(pr.getSpecialWeapon()) : null)
									.enemiesDefeated(pr.getDefeatEnemyCount())
									.goldenEggsAssisted(pr.getGoldenAssistCount())
									.goldenEggsDelivered(pr.getGoldenDeliverCount())
									.rescueCount(pr.getRescueCount())
									.rescuedCount(pr.getRescuedCount())
									.normalEggsDelivered(pr.getDeliverCount())
									.title(pr.getPlayer().getByname())
									.uniform(ensureUniformExists(pr.getPlayer().getUniform()))
									.build()
							));
				})
			.collect(Collectors.toList());
	}

	public Splatoon3SrUniform ensureUniformExists(IdAndNameAndImage uniform) {
		var dbUniform = uniformRepository.findByApiId(uniform.getId())
			.orElseGet(() ->
				uniformRepository.save(
					Splatoon3SrUniform.builder()
						.apiId(uniform.getId())
						.name(uniform.getName())
						.image(imageService.ensureExists(uniform.getImage().getUrl()))
						.build()
				));

		if (imageService.isFailed(dbUniform.getImage())
			&& !dbUniform.getImage().getUrl().equals(uniform.getImage().getUrl())) {

			dbUniform = uniformRepository.save(dbUniform.toBuilder()
				.image(imageService.ensureExists(uniform.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for sr uniform with id `%d` to `%s`", dbUniform.getId(), uniform.getImage().getUrl()));
		}

		return dbUniform;
	}

	public List<Splatoon3SrResultWave> ensureWaveExists(Splatoon3SrResult dbResult, List<WaveResults> waveResults) {
		return waveResults.stream()
			.map(wr -> resultWaveRepository.findByResultIdAndWaveNumber(dbResult.getId(), wr.getWaveNumber())
				.orElseGet(() -> {
					var dbWr = resultWaveRepository.save(Splatoon3SrResultWave.builder()
						.resultId(dbResult.getId())
						.waveNumber(wr.getWaveNumber())
						.waterLevel(wr.getWaterLevel())
						.goldenEggsRequired(wr.getDeliverNorm())
						.goldenEggsSpawned(wr.getGoldenPopCount())
						.goldenEggsDelivered(wr.getTeamDeliverCount())
						.eventWave(wr.getEventWave() != null ? ensureEventWaveExists(wr.getEventWave()) : null)
						.build());

					ensureResultWaveUsedSpecialWeaponsExist(dbWr, wr);

					return dbWr;
				}))
			.collect(Collectors.toList());
	}

	public List<Splatoon3SrResultWaveUsedSpecialWeapon> ensureResultWaveUsedSpecialWeaponsExist(Splatoon3SrResultWave dbWr, WaveResults wr) {
		var currentWaveSpecials = new ArrayList<>(resultWaveUsedSpecialWeaponRepository.findAllByResultWave(dbWr));

		var missingSpecials = new ArrayList<Splatoon3SrResultWaveUsedSpecialWeapon>();
		for (var special : wr.getSpecialWeapons()) {
			var first = currentWaveSpecials.stream()
				.filter(cws -> cws.getSpecialWeapon().getApiId().equals(special.getId()))
				.findFirst()
				.orElse(null);

			if (first != null) {
				// match -> remove both
				currentWaveSpecials.remove(first);
			} else {
				// missing in database -> add to create list
				missingSpecials.add(Splatoon3SrResultWaveUsedSpecialWeapon.builder()
					.resultWave(dbWr)
					.specialWeapon(ensureSpecialWeaponExists(special))
					.build());
			}
		}

		if (missingSpecials.size() > 0) {
			resultWaveUsedSpecialWeaponRepository.saveAll(missingSpecials);
		}

		if (currentWaveSpecials.size() > 0) {
			// what's left in currentWaveSpecials did not happen in the wave -> remove
			resultWaveUsedSpecialWeaponRepository.deleteAll(currentWaveSpecials);
		}

		return resultWaveUsedSpecialWeaponRepository.findAllByResultWave(dbWr);
	}

	public Splatoon3SrSpecialWeapon ensureSpecialWeaponExists(Weapon special) {
		var dbSpecial = specialWeaponRepository.findByName(special.getName())
			.orElseGet(() ->
				specialWeaponRepository.save(
					Splatoon3SrSpecialWeapon.builder()
						.apiId(special.getId())
						.name(special.getName())
						.image(imageService.ensureExists(special.getImage().getUrl()))
						.build()
				));

		if (imageService.isFailed(dbSpecial.getImage())
			&& !dbSpecial.getImage().getUrl().equals(special.getImage().getUrl())) {

			dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
				.image(imageService.ensureExists(special.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for sr special with id `%d` to `%s`", dbSpecial.getId(), special.getImage().getUrl()));
		}

		try {
			if (dbSpecial.getApiId() == null) {
				if (special.getId() != null) {
					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.apiId(special.getId())
						.build());
				} else if (dbSpecial.getWeaponId() != null) {
					var apiId = Base64.getEncoder()
						.encodeToString(String.format("SpecialWeapon-%d", dbSpecial.getWeaponId()).getBytes(StandardCharsets.UTF_8));

					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.apiId(apiId)
						.build());
				} else if (special.getWeaponId() != null) {
					var apiId = Base64.getEncoder()
						.encodeToString(String.format("SpecialWeapon-%d", special.getWeaponId()).getBytes(StandardCharsets.UTF_8));

					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.apiId(apiId)
						.build());
				}
			} else if (special.getId() != null && !dbSpecial.getApiId().equals(special.getId())) {
				dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
					.apiId(special.getId())
					.build());
			}
		} catch (Exception ignored) {
		}

		try {
			if (dbSpecial.getWeaponId() == null) {
				if (special.getWeaponId() != null) {
					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.weaponId(special.getWeaponId())
						.build());
				} else if (dbSpecial.getApiId() != null) {
					var weaponId = Long.parseLong(
						new String(Base64.getDecoder().decode(dbSpecial.getApiId())).replaceAll("[^0-9]", ""));

					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.weaponId(weaponId)
						.build());
				} else if (special.getId() != null) {
					var weaponId = Long.parseLong(
						new String(Base64.getDecoder().decode(special.getId())).replaceAll("[^0-9]", ""));

					dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
						.weaponId(weaponId)
						.build());
				}
			} else if (special.getWeaponId() != null && !dbSpecial.getWeaponId().equals(special.getWeaponId())) {
				dbSpecial = specialWeaponRepository.save(dbSpecial.toBuilder()
					.weaponId(special.getWeaponId())
					.build());
			}
		} catch (Exception ignored) {
		}

		return dbSpecial;
	}

	public Splatoon3SrEventWave ensureEventWaveExists(IdAndName eventWave) {
		return eventWaveRepository.findByApiId(eventWave.getId())
			.orElseGet(() ->
				eventWaveRepository.save(
					Splatoon3SrEventWave.builder()
						.apiId(eventWave.getId())
						.name(eventWave.getName())
						.build()
				));
	}

	public Splatoon3SrGrade ensureGradeExists(IdAndName afterGrade) {
		if (afterGrade == null) return null;

		return gradeRepository.findByApiId(afterGrade.getId())
			.orElseGet(() ->
				gradeRepository.save(
					Splatoon3SrGrade.builder()
						.apiId(afterGrade.getId())
						.name(afterGrade.getName())
						.build()
				));
	}

	private static boolean getSuccessful(List<BossResult> br, WaveResults wr) {
		return br != null && !br.isEmpty()
			? br.stream().allMatch(BossResult::getHasDefeatBoss)
			: wr.getTeamDeliverCount() >= wr.getDeliverNorm();
	}

	public Instant findStartTimeOfLatestGame() {
		return resultRepository.findTop1ByOrderByPlayedTimeDesc()
			.map(Splatoon3SrResult::getPlayedTime)
			.orElse(Instant.ofEpochSecond(0));
	}

	public boolean notFound(BattleResults.HistoryGroupMatch hgn) {
		return resultRepository.findByApiId(hgn.getId()).isEmpty();
	}
}

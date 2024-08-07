package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResults;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class Splatoon3VsResultService {
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final ObjectMapper mapper = new ObjectMapper();

	private final ImageService imageService;
	private final Splatoon3GeneralService generalService;
	private final Splatoon3VsRotationService rotationService;

	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3VsModeRepository modeRepository;
	private final Splatoon3VsResultTeamRepository teamRepository;
	private final Splatoon3VsResultTeamPlayerRepository teamPlayerRepository;
	private final Splatoon3VsWeaponRepository weaponRepository;
	private final Splatoon3VsSubWeaponRepository subWeaponRepository;
	private final Splatoon3VsSpecialWeaponRepository specialWeaponRepository;
	private final Splatoon3VsGearRepository gearRepository;
	private final Splatoon3VsBrandRepository brandRepository;
	private final Splatoon3VsAbilityRepository abilityRepository;
	private final Splatoon3VsAwardRepository awardRepository;

	@Transactional
	public Splatoon3VsResult ensureResultExists(VsHistoryDetail game, String json) {
		var mode = modeRepository.findByApiId(game.getVsMode().getId()).orElseThrow();
		var rule = rotationService.ensureRuleExists(game.getVsRule());
		var stage = rotationService.ensureStageExists(game.getVsStage());
		var eventRegulation = Optional.ofNullable(game.getLeagueMatch()).map(LeagueMatchDetails::getLeagueMatchEvent).orElse(null);

		var result = resultRepository.findByApiId(game.getId()).stream()
			.findFirst()
			.orElseGet(() -> {
				var newResult = resultRepository.save(Splatoon3VsResult.builder()
					.apiId(game.getId())

					.playedTime(game.getPlayedTimeAsInstant())
					.duration(game.getDuration())

					.rotation(rotationService.ensureDummyRotationExists(mode, rule, stage, game.getPlayedTimeAsInstant(), eventRegulation))
					.mode(mode)
					.rule(rule)
					.stage(stage)

					.ownJudgement(game.getJudgement())
					.knockout(game.getKnockout())

					.awards(game.getAwards().stream().map(this::ensureAwardExists).collect(Collectors.toList()))

					.shortenedJson(imageService.shortenJson(json))
					.build());

				log.info("Created new vs result id: {}, played time: '{}', result: '{}', mode: '{}', rule: '{}', stage: {}", newResult.getId(), newResult.getPlayedTime(), newResult.getOwnJudgement(), newResult.getMode().getName(), newResult.getRule().getName(), newResult.getStage().getName());

				return newResult;
			});

		var allTeams = new ArrayList<Team>();
		allTeams.add(game.getMyTeam());
		allTeams.addAll(game.getOtherTeams());

		var teams = allTeams.stream()
			.map(t -> ensureTeamExists(t, result))
			.collect(Collectors.toList());

		return resultRepository.save(result.toBuilder()
			.teams(teams)
			.build());
	}

	@Transactional
	public Splatoon3VsResultTeam ensureTeamExists(Team team, Splatoon3VsResult result) {
		var s3Team = teamRepository.findByResultIdAndTeamOrder(result.getId(), team.getOrder())
			.orElseGet(() ->
				teamRepository.save(Splatoon3VsResultTeam.builder()
					.resultId(result.getId())
					.teamOrder(team.getOrder())

					.isMyTeam(team.getPlayers().stream().anyMatch(Player::getIsMyself))

					.inkColorR(team.getColor().getR())
					.inkColorG(team.getColor().getG())
					.inkColorB(team.getColor().getB())
					.inkColorA(team.getColor().getA())

					.judgement(team.getJudgement())

					.paintRatio(team.getResult() != null ? team.getResult().getPaintRatio() : null)
					.score(team.getResult() != null ? team.getResult().getScore() : null)

					.splatfestTeamName(team.getFestTeamName())
					.splatfestStreakWinCount(team.getFestStreakWinCount())
					.splatfestUniformBonusRate(team.getFestUniformBonusRate())
					.splatfestUniformName(team.getFestUniformName())

					.tricolorRole(team.getTricolorRole())
					.tricolorGainedUltraSignals(team.getResult() != null ? team.getResult().getNoroshi() : null)

					.build()));

		var teamPlayers = team.getPlayers().stream().map(p -> ensureTeamPlayerExists(s3Team, p)).collect(Collectors.toList());

		return teamRepository.save(s3Team.toBuilder()
			.teamPlayers(teamPlayers)
			.build());
	}


	@Transactional
	public Splatoon3VsResultTeamPlayer ensureTeamPlayerExists(Splatoon3VsResultTeam team, Player player) {
		var s3Player = generalService.ensurePlayerExists(player.getId());

		var badgeLeft = player.getNameplate().getBadges().get(0) != null
			? generalService.ensureBadgeExists(player.getNameplate().getBadges().get(0), player.getIsMyself())
			: null;

		var badgeMiddle = player.getNameplate().getBadges().get(1) != null
			? generalService.ensureBadgeExists(player.getNameplate().getBadges().get(1), player.getIsMyself())
			: null;

		var badgeRight = player.getNameplate().getBadges().get(2) != null
			? generalService.ensureBadgeExists(player.getNameplate().getBadges().get(2), player.getIsMyself())
			: null;

		var headGearAbility2 = player.getHeadGear().getAdditionalGearPowers().size() > 1
			? ensureAbilityExists(player.getHeadGear().getAdditionalGearPowers().get(1))
			: null;

		var headGearAbility3 = player.getHeadGear().getAdditionalGearPowers().size() > 2
			? ensureAbilityExists(player.getHeadGear().getAdditionalGearPowers().get(2))
			: null;

		var clothingGearAbility2 = player.getClothingGear().getAdditionalGearPowers().size() > 1
			? ensureAbilityExists(player.getClothingGear().getAdditionalGearPowers().get(1))
			: null;

		var clothingGearAbility3 = player.getClothingGear().getAdditionalGearPowers().size() > 2
			? ensureAbilityExists(player.getClothingGear().getAdditionalGearPowers().get(2))
			: null;

		var shoesGearAbility2 = player.getShoesGear().getAdditionalGearPowers().size() > 1
			? ensureAbilityExists(player.getShoesGear().getAdditionalGearPowers().get(1))
			: null;

		var shoesGearAbility3 = player.getShoesGear().getAdditionalGearPowers().size() > 2
			? ensureAbilityExists(player.getShoesGear().getAdditionalGearPowers().get(2))
			: null;

		return teamPlayerRepository.findByResultIdAndTeamOrderAndPlayerId(team.getResultId(), team.getTeamOrder(), s3Player.getId())
			.orElseGet(() ->
				teamPlayerRepository.save(Splatoon3VsResultTeamPlayer.builder()
					.resultId(team.getResultId())
					.teamOrder(team.getTeamOrder())
					.playerId(s3Player.getId())

					.isMyself(player.getIsMyself())
					.name(player.getName())
					.nameId(player.getNameId())
					.species(player.getSpecies())

					.nameplate(generalService.ensureNameplateExists(player.getNameplate(), player.getIsMyself()))
					.title(player.getByname())
					.badgeLeft(badgeLeft)
					.badgeMiddle(badgeMiddle)
					.badgeRight(badgeRight)

					.weapon(ensureWeaponExists(player.getWeapon()))

					.paint(player.getPaint())
					.kills(player.getResult() != null ? player.getResult().getKill() : null)
					.assists(player.getResult() != null ? player.getResult().getAssist() : null)
					.specials(player.getResult() != null ? player.getResult().getSpecial() : null)
					.deaths(player.getResult() != null ? player.getResult().getDeath() : null)
					.ultraSignalAttempts(player.getResult() != null ? player.getResult().getNoroshiTry() : null)

					.hasCrown(player.getCrown())
					.splatfestDragonCert(player.getFestDragonCert())

					.headGear(ensureGearExists(player.getHeadGear()))
					.headGearMainAbility(ensureAbilityExists(player.getHeadGear().getPrimaryGearPower()))
					.headGearSecondaryAbility1(ensureAbilityExists(player.getHeadGear().getAdditionalGearPowers().get(0)))
					.headGearSecondaryAbility2(headGearAbility2)
					.headGearSecondaryAbility3(headGearAbility3)

					.clothingGear(ensureGearExists(player.getClothingGear()))
					.clothingMainAbility(ensureAbilityExists(player.getClothingGear().getPrimaryGearPower()))
					.clothingSecondaryAbility1(ensureAbilityExists(player.getClothingGear().getAdditionalGearPowers().get(0)))
					.clothingSecondaryAbility2(clothingGearAbility2)
					.clothingSecondaryAbility3(clothingGearAbility3)

					.shoesGear(ensureGearExists(player.getShoesGear()))
					.shoesMainAbility(ensureAbilityExists(player.getShoesGear().getPrimaryGearPower()))
					.shoesSecondaryAbility1(ensureAbilityExists(player.getShoesGear().getAdditionalGearPowers().get(0)))
					.shoesSecondaryAbility2(shoesGearAbility2)
					.shoesSecondaryAbility3(shoesGearAbility3)
					.build()));
	}


	@Transactional
	public Splatoon3VsGear ensureGearExists(Gear gear) {
		var dbGear = gearRepository.findByName(gear.getName())
			.orElseGet(() ->
				gearRepository.save(
					Splatoon3VsGear.builder()
						.name(gear.getName())
						.type(gear.get__isGear())
						.originalImage(imageService.ensureExists(gear.getOriginalImage().getUrl()))
						.thumbnailImage(imageService.ensureExists(gear.getThumbnailImage().getUrl()))
						.brand(ensureBrandExists(gear.getBrand()))
						.build()
				));

		var changed = false;

		if (imageService.isFailed(dbGear.getOriginalImage())
			&& !dbGear.getOriginalImage().getUrl().equals(gear.getOriginalImage().getUrl())) {

			changed = true;
			dbGear = dbGear.toBuilder()
				.originalImage(imageService.ensureExists(gear.getOriginalImage().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set original image for gear with id `%d` to `%s`", dbGear.getId(), gear.getOriginalImage().getUrl()));
		}

		if (imageService.isFailed(dbGear.getThumbnailImage())
			&& !dbGear.getThumbnailImage().getUrl().equals(gear.getThumbnailImage().getUrl())) {

			changed = true;
			dbGear = dbGear.toBuilder()
				.thumbnailImage(imageService.ensureExists(gear.getThumbnailImage().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set thumbnail image for gear with id `%d` to `%s`", dbGear.getId(), gear.getThumbnailImage().getUrl()));
		}

		if (changed) {
			dbGear = gearRepository.save(dbGear);
		}

		return dbGear;
	}


	@Transactional
	public Splatoon3VsBrand ensureBrandExists(Brand brand) {
		var dbBrand = brandRepository.findByApiId(brand.getId())
			.orElseGet(() ->
				brandRepository.save(
					Splatoon3VsBrand.builder()
						.apiId(brand.getId())
						.name(brand.getName())
						.image(imageService.ensureExists(brand.getImage().getUrl()))
						.favoredAbility(ensureAbilityExists(brand.getUsualGearPower()))
						.build()
				));

		if (dbBrand.getFavoredAbility() == null && brand.getUsualGearPower() != null) {
			dbBrand = brandRepository.save(
				dbBrand.toBuilder()
					.favoredAbility(ensureAbilityExists(brand.getUsualGearPower()))
					.build()
			);
		}

		if (imageService.isFailed(dbBrand.getImage())
			&& !dbBrand.getImage().getUrl().equals(brand.getImage().getUrl())) {

			dbBrand = brandRepository.save(dbBrand.toBuilder()
				.image(imageService.ensureExists(brand.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for brand with id `%d` to `%s`", dbBrand.getId(), brand.getImage().getUrl()));
		}

		return dbBrand;
	}


	@Transactional
	public Splatoon3VsAbility ensureAbilityExists(GearPower usualGearPower) {
		if (usualGearPower == null) return null;

		var dbAbility = abilityRepository.findByName(usualGearPower.getName())
			.orElseGet(() ->
				abilityRepository.save(
					Splatoon3VsAbility.builder()
						.name(usualGearPower.getName())
						.description(usualGearPower.getDesc())
						.image(imageService.ensureExists(usualGearPower.getImage().getUrl()))
						.isFillerAbility(usualGearPower.getIsEmptySlot() != null
							? usualGearPower.getIsEmptySlot()
							: usualGearPower.getName().equalsIgnoreCase("unknown"))
						.build()
				));

		if (imageService.isFailed(dbAbility.getImage())
			&& !dbAbility.getImage().getUrl().equals(usualGearPower.getImage().getUrl())) {

			dbAbility = abilityRepository.save(dbAbility.toBuilder()
				.image(imageService.ensureExists(usualGearPower.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for ability with id `%d` to `%s`", dbAbility.getId(), usualGearPower.getImage().getUrl()));
		}

		return dbAbility;
	}


	@Transactional
	public Splatoon3VsWeapon ensureWeaponExists(Weapon weapon) {
		var dbWeapon = weaponRepository.findByApiId(weapon.getId())
			.orElseGet(() ->
				weaponRepository.save(
					Splatoon3VsWeapon.builder()
						.apiId(weapon.getId())
						.name(weapon.getName())
						.subWeapon(ensureSubWeaponExists(weapon.getSubWeapon()))
						.specialWeapon(ensureSpecialWeaponExists(weapon.getSpecialWeapon()))
						.image(imageService.ensureExists(weapon.getImage().getUrl()))
						.image2D(imageService.ensureExists(weapon.getImage2d().getUrl()))
						.image2DThumbnail(imageService.ensureExists(weapon.getImage2dThumbnail().getUrl()))
						.image3D(imageService.ensureExists(weapon.getImage3d().getUrl()))
						.image3DThumbnail(imageService.ensureExists(weapon.getImage3dThumbnail().getUrl()))
						.build()
				));

		var changed = false;

		var subWeapon = ensureSubWeaponExists(weapon.getSubWeapon());
		if (dbWeapon.getSubWeapon() != subWeapon) {
			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.subWeapon(subWeapon)
				.build();
		}

		var specialWeapon = ensureSpecialWeaponExists(weapon.getSpecialWeapon());
		if (dbWeapon.getSpecialWeapon() != specialWeapon) {
			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.specialWeapon(specialWeapon)
				.build();
		}

		if (imageService.isFailed(dbWeapon.getImage())
			&& !dbWeapon.getImage().getUrl().equals(weapon.getImage().getUrl())) {

			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.image(imageService.ensureExists(weapon.getImage().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set image for weapon with id `%d` to `%s`", dbWeapon.getId(), weapon.getImage().getUrl()));
		}

		if (imageService.isFailed(dbWeapon.getImage2D())
			&& !dbWeapon.getImage().getUrl().equals(weapon.getImage2d().getUrl())) {

			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.image2D(imageService.ensureExists(weapon.getImage2d().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set 2d image for weapon with id `%d` to `%s`", dbWeapon.getId(), weapon.getImage2d().getUrl()));
		}

		if (imageService.isFailed(dbWeapon.getImage2DThumbnail())
			&& !dbWeapon.getImage().getUrl().equals(weapon.getImage2dThumbnail().getUrl())) {

			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.image2DThumbnail(imageService.ensureExists(weapon.getImage2dThumbnail().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set 2d thumbnail image for weapon with id `%d` to `%s`", dbWeapon.getId(), weapon.getImage2dThumbnail().getUrl()));
		}

		if (imageService.isFailed(dbWeapon.getImage3D())
			&& !dbWeapon.getImage().getUrl().equals(weapon.getImage3d().getUrl())) {

			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.image3D(imageService.ensureExists(weapon.getImage3d().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set 3d image for weapon with id `%d` to `%s`", dbWeapon.getId(), weapon.getImage3d().getUrl()));
		}

		if (imageService.isFailed(dbWeapon.getImage3DThumbnail())
			&& !dbWeapon.getImage().getUrl().equals(weapon.getImage3dThumbnail().getUrl())) {

			changed = true;
			dbWeapon = dbWeapon.toBuilder()
				.image3DThumbnail(imageService.ensureExists(weapon.getImage3dThumbnail().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set 3d thumbnail image for weapon with id `%d` to `%s`", dbWeapon.getId(), weapon.getImage3dThumbnail().getUrl()));
		}

		if (changed) {
			dbWeapon = weaponRepository.save(dbWeapon);
		}

		return dbWeapon;
	}


	@Transactional
	public Splatoon3VsSubWeapon ensureSubWeaponExists(WeaponDetail subWeapon) {
		var dbSubWeapon = subWeaponRepository.findByApiId(subWeapon.getId())
			.orElseGet(() ->
				subWeaponRepository.save(
					Splatoon3VsSubWeapon.builder()
						.apiId(subWeapon.getId())
						.name(subWeapon.getName())
						.image(imageService.ensureExists(subWeapon.getImage().getUrl()))
						.build()
				));

		if (imageService.isFailed(dbSubWeapon.getImage())
			&& !dbSubWeapon.getImage().getUrl().equals(subWeapon.getImage().getUrl())) {

			dbSubWeapon = subWeaponRepository.save(dbSubWeapon.toBuilder()
				.image(imageService.ensureExists(subWeapon.getImage().getUrl()))
				.build());

			logSender.sendLogs(log, String.format("Set image for sub weapon with id `%d` to `%s`", dbSubWeapon.getId(), subWeapon.getImage().getUrl()));
		}

		return dbSubWeapon;
	}


	@Transactional
	public Splatoon3VsSpecialWeapon ensureSpecialWeaponExists(WeaponDetail specialWeapon) {
		var dbSpecialWeapon = specialWeaponRepository.findByApiId(specialWeapon.getId())
			.orElseGet(() ->
				specialWeaponRepository.save(
					Splatoon3VsSpecialWeapon.builder()
						.apiId(specialWeapon.getId())
						.name(specialWeapon.getName())
						.image(imageService.ensureExists(specialWeapon.getImage().getUrl()))
						.overlayImage(imageService.ensureExists(specialWeapon.getMaskingImage().getOverlayImageUrl()))
						.maskingImage(imageService.ensureExists(specialWeapon.getMaskingImage().getMaskImageUrl()))
						.maskingImageWidth(specialWeapon.getMaskingImage().getWidth())
						.maskingImageHeight(specialWeapon.getMaskingImage().getHeight())
						.build()
				));

		var changed = false;

		if (imageService.isFailed(dbSpecialWeapon.getImage())
			&& !dbSpecialWeapon.getImage().getUrl().equals(specialWeapon.getImage().getUrl())) {

			changed = true;
			dbSpecialWeapon = dbSpecialWeapon.toBuilder()
				.image(imageService.ensureExists(specialWeapon.getImage().getUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set image for special weapon with id `%d` to `%s`", dbSpecialWeapon.getId(), specialWeapon.getImage().getUrl()));
		}

		if (imageService.isFailed(dbSpecialWeapon.getOverlayImage())
			&& !dbSpecialWeapon.getOverlayImage().getUrl().equals(specialWeapon.getMaskingImage().getOverlayImageUrl())) {

			changed = true;
			dbSpecialWeapon = dbSpecialWeapon.toBuilder()
				.overlayImage(imageService.ensureExists(specialWeapon.getMaskingImage().getOverlayImageUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set overlay image for special weapon with id `%d` to `%s`", dbSpecialWeapon.getId(), specialWeapon.getMaskingImage().getOverlayImageUrl()));
		}

		if (imageService.isFailed(dbSpecialWeapon.getMaskingImage())
			&& !dbSpecialWeapon.getMaskingImage().getUrl().equals(specialWeapon.getMaskingImage().getMaskImageUrl())) {

			changed = true;
			dbSpecialWeapon = dbSpecialWeapon.toBuilder()
				.maskingImage(imageService.ensureExists(specialWeapon.getMaskingImage().getMaskImageUrl()))
				.build();

			logSender.sendLogs(log, String.format("Set masking image for special weapon with id `%d` to `%s`", dbSpecialWeapon.getId(), specialWeapon.getMaskingImage().getMaskImageUrl()));
		}

		if (changed) {
			dbSpecialWeapon = specialWeaponRepository.save(dbSpecialWeapon);
		}

		return dbSpecialWeapon;
	}


	@Transactional
	public Splatoon3VsAward ensureAwardExists(Award award) {
		return awardRepository.findByName(award.getName())
			.orElseGet(() ->
				awardRepository.save(
					Splatoon3VsAward.builder()
						.name(award.getName())
						.rank(award.getRank())
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

	public Instant findStartTimeOfLatestGame() {
		return resultRepository.findTop1ByOrderByPlayedTimeDesc()
			.map(Splatoon3VsResult::getPlayedTime)
			.orElse(Instant.ofEpochSecond(0));
	}

	public boolean notFound(BattleResults.HistoryGroupMatch hgn) {
		return resultRepository.findByApiId(hgn.getId()).isEmpty();
	}

	public void fixDoubledEntries() {
		var alleDoubledEntries = resultRepository.findDoubledEntries();

		if (!alleDoubledEntries.isEmpty()) {
			logSender.sendLogs(log, String.format("Found `%d` results with doubled api id", alleDoubledEntries.size()));
		}

		for (var entry : alleDoubledEntries) {
			var apiId = entry.getApiId();

			logSender.sendLogs(log, String.format("Trying to reduce doubled result entries for api id: `%s`", apiId));

			var allGames = resultRepository.findByApiId(apiId);

			try {
				var gameToKeep = allGames.stream().findFirst().orElseThrow();
				logSender.sendLogs(log, String.format("game with id: `%d` will be kept for api id: `%s`", gameToKeep.getId(), apiId));

				for (var game : allGames) {
					if (!Objects.equals(game.getId(), gameToKeep.getId())) {
						logSender.sendLogs(log, String.format("deleting game with id: `%d` for api id: `%s`", game.getId(), apiId));

						game.getTeams().forEach(t -> teamPlayerRepository.deleteAll(t.getTeamPlayers()));
						teamRepository.deleteAll(game.getTeams());
						resultRepository.delete(game);
					}
				}
				logSender.sendLogs(log, String.format("Done with entries for api id: `%s`", apiId));
			} catch (Exception ex) {
				logSender.sendLogs(log, String.format("An exception occurred during removal of doubled result entries for api id: `%s`", apiId));
				exceptionLogger.logException(log, ex);
			}
		}
	}
}

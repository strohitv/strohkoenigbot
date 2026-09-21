package tv.strohi.twitch.strohkoenigbot.splatoon3saver.stream;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3GearDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3SpecialWeaponWinStatsDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3WeaponStatsDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3XPowerDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStatsWithRule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.FullscreenStreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.IconBadgeNames;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.StreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Stats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class StatsFullscreenFiller implements ScheduledService {
	private final LoadedSplatNetObjects loadedSplatNetObjects;

	private final TwitchBotClient twitchBotClient;
	private final ImageService imageService;

	private final S3WeaponStatsDownloader weaponStatsDownloader;
	private final S3SpecialWeaponWinStatsDownloader specialWeaponWinStatsDownloader;

	private final Splatoon3BadgeRepository badgeRepository;
	private final Splatoon3VsGearRepository gearRepository;
	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3VsWeaponRepository weaponRepository;
	private final Splatoon3VsSubWeaponRepository subWeaponRepository;
	private final Splatoon3VsStageRepository stageRepository;

	private final Splatoon3VsGearChunkGainRepository chunkGainRepository;

	private final S3GearDownloader gearDownloader;

	@Getter
	private FullscreenStreamData fullscreenStreamData = FullscreenStreamData.empty();

	private int chunksGainedStream = 0;
	private List<SpecialWinCount> specialWinStatsAtStreamStart = null;
	private Weapon[] weaponStatsAtStreamStart = null;
	private List<OwnUsedWeaponStatsWithWeapon> ownUsedWeaponWinStatsAtStart = null;
	private List<StageWinStatsWithRule> stageResultStatsAtStart = null;

	private void run() {
		if (twitchBotClient.getWentLiveTime() == null
			|| loadedSplatNetObjects.getWeapons().isEmpty()
			|| loadedSplatNetObjects.getSpecialWinCounts().isEmpty()
			|| loadedSplatNetObjects.getParsedLastGame().isEmpty()
			|| loadedSplatNetObjects.getParsedHistory().isEmpty()
			|| loadedSplatNetObjects.getHeadGameCount().isEmpty()
			|| loadedSplatNetObjects.getShirtGameCount().isEmpty()
			|| loadedSplatNetObjects.getShoesGameCount().isEmpty()
			|| loadedSplatNetObjects.getStageWins().isEmpty()) {
			fullscreenStreamData = FullscreenStreamData.empty();

			chunksGainedStream = 0;
			specialWinStatsAtStreamStart = null;
			weaponStatsAtStreamStart = null;
			ownUsedWeaponWinStatsAtStart = null;
			stageResultStatsAtStart = null;

			return;
		}

		final var allGamesInStream = resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc(twitchBotClient.getWentLiveTime());

		if (allGamesInStream.isEmpty()) {
			fullscreenStreamData = FullscreenStreamData.empty();
			return;
		}

		if (weaponStatsAtStreamStart == null) {
			weaponStatsAtStreamStart = weaponStatsDownloader.downloadWeaponStats().orElse(null);
		}

		if (specialWinStatsAtStreamStart == null) {
			specialWinStatsAtStreamStart = specialWeaponWinStatsDownloader.downloadSpecialWeaponStats(twitchBotClient.getWentLiveTime()).orElse(null);
		}

		if (ownUsedWeaponWinStatsAtStart == null) {
			ownUsedWeaponWinStatsAtStart = weaponRepository.getWeaponResultStatsForAllWeapons(twitchBotClient.getWentLiveTime());
		}

		if (stageResultStatsAtStart == null) {
			stageResultStatsAtStart = stageRepository.findAllStageWinStats(twitchBotClient.getWentLiveTime());
		}

		final var lastGame = allGamesInStream.get(allGamesInStream.size() - 1);
		final var ownPlayer = getOwnPlayer(lastGame);

		// Team Stats
		final var ownTeam = lastGame.getTeams().stream()
			.filter(Splatoon3VsResultTeam::getIsMyTeam)
			.findFirst()
			.orElse(null);
		final var opp1 = lastGame.getTeams().stream()
			.filter(t -> !t.getIsMyTeam())
			.findFirst()
			.orElse(null);

		if (ownTeam == null || opp1 == null) {
			// draw, do nothing
			return;
		}

		final var opp2 = lastGame.getTeams().stream()
			.filter(t -> !t.equals(ownTeam) && !t.equals(opp1))
			.findFirst()
			.orElse(null);

		// Weapon Stats
		final var weaponStats = loadedSplatNetObjects.getWeapons().get();
		final var specialWinStats = loadedSplatNetObjects.getSpecialWinCounts();

		// Game Stats
		final var ownUsedWeaponStats = Arrays.stream(weaponStats)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStats == null) {
			return;
		}

		final var ownUsedWeaponStatsAtStart = Arrays.stream(weaponStatsAtStreamStart)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStatsAtStart == null) {
			return;
		}

		var parsedOriginalResult = loadedSplatNetObjects.getParsedLastGame().get();
		var history = loadedSplatNetObjects.getParsedHistory().get();

		var parsedOwnPlayer = parsedOriginalResult.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
			.filter(Player::getIsMyself)
			.findFirst()
			.orElse(parsedOriginalResult.getData().getVsHistoryDetail().getPlayer());

		var allXPowers = loadedSplatNetObjects.getXPowers().orElse(new S3XPowerDownloader.Powers(null, null, null, null));
		var allWeaponResultStats = weaponRepository.getWeaponResultStats(ownPlayer.getWeapon().getId());

		var weaponResultStats = allWeaponResultStats.stream()
			.map(w -> new FullscreenStreamData.KeyWinDefeatRate(
				shortenModeName(w.getModeName()),
				FullscreenStreamData.WinDefeatRate.builder()
					.wins(w.getTotalWins())
					.wins_gained(w.getTotalWins() - ownUsedWeaponWinStatsAtStart.stream()
						.filter(was -> was.getWeaponId() == ownPlayer.getWeapon().getId() && Objects.equals(was.getModeName(), w.getModeName()))
						.findFirst()
						.map(OwnUsedWeaponStatsWithWeapon::getTotalWins)
						.orElse(0L))
					.defeats(w.getTotalDefeats())
					.defeats_gained(w.getTotalDefeats() - ownUsedWeaponWinStatsAtStart.stream()
						.filter(was -> was.getWeaponId() == ownPlayer.getWeapon().getId() && Objects.equals(was.getModeName(), w.getModeName()))
						.findFirst()
						.map(OwnUsedWeaponStatsWithWeapon::getTotalDefeats)
						.orElse(0L))
					.winrate(w.getWinRate())
					.build()))
			.collect(Collectors.toCollection(ArrayList::new));

		var emptyWeaponWinStats = new FullscreenStreamData.KeyWinDefeatRate("Total", FullscreenStreamData.WinDefeatRate.builder()
			.wins(0)
			.wins_gained(0)
			.defeats(0)
			.defeats_gained(0)
			.winrate(0.0)
			.build());

		var totalWeaponWinStats = Stream.of(
				weaponResultStats.stream(),
				Stream.of(emptyWeaponWinStats))
			.flatMap(a -> a)
			.reduce((a, b) -> FullscreenStreamData.KeyWinDefeatRate.builder()
				.key("Total")
				.win_defeat_rate(FullscreenStreamData.WinDefeatRate.builder()
					.wins(a.getWin_defeat_rate().getWins() + b.getWin_defeat_rate().getWins())
					.wins_gained(a.getWin_defeat_rate().getWins_gained() + b.getWin_defeat_rate().getWins_gained())
					.defeats(a.getWin_defeat_rate().getDefeats() + b.getWin_defeat_rate().getDefeats())
					.defeats_gained(a.getWin_defeat_rate().getDefeats_gained() + b.getWin_defeat_rate().getDefeats_gained())
					.winrate(100.0 * (a.getWin_defeat_rate().getWins() + b.getWin_defeat_rate().getWins()) / Math.max(1, a.getWin_defeat_rate().getWins() + b.getWin_defeat_rate().getWins() + a.getWin_defeat_rate().getDefeats() + b.getWin_defeat_rate().getDefeats()))
					.build())
				.build())
			.orElse(emptyWeaponWinStats);

		weaponResultStats.add(totalWeaponWinStats);

		var usedSubWeaponResultStats = subWeaponRepository.getWeaponResultStats(ownPlayer.getWeapon().getSubWeapon().getId())
			.stream()
			.findFirst()
			.orElse(new OwnUsedWeaponStatsWithWeapon("Total", ownPlayer.getWeapon().getSubWeapon().getId(), 0L, 0L, 0L));

		var totalGameCount = totalWeaponWinStats.getWin_defeat_rate().getWins() + totalWeaponWinStats.getWin_defeat_rate().getDefeats();

		if (gearDownloader.getCachedGears().isEmpty()) {
			return;
		}

		final var ownWeaponExpAtStart = getWeaponExp(ownUsedWeaponStatsAtStart.getStats().getLevel(), ownUsedWeaponStatsAtStart.getStats().getExpToLevelUp());
		final var ownWeaponExpNow = getWeaponExp(ownUsedWeaponStats.getStats().getLevel(), ownUsedWeaponStats.getStats().getExpToLevelUp());

		final var expWeaponGain = ownWeaponExpNow - ownWeaponExpAtStart;
		final var ownWeaponExpGoal = getExpGoal(ownUsedWeaponStats.getStats().getLevel());

		var alreadyOwnedExpRatio = ownWeaponExpAtStart * 100.0 / ownWeaponExpGoal;
		var earnedExpStreamRatio = expWeaponGain * 100.0 / ownWeaponExpGoal;
		var remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;

		if (ownUsedWeaponStats.getStats().getLevel() >= 5) {
			var previousLevelExp = getExpGoal(ownUsedWeaponStats.getStats().getLevel() - 1);
			alreadyOwnedExpRatio = (ownWeaponExpAtStart - previousLevelExp) * 100.0 / (ownWeaponExpGoal - previousLevelExp);
			earnedExpStreamRatio = expWeaponGain * 100.0 / (ownWeaponExpGoal - previousLevelExp);
			remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;
		}

		final var ownUsedSpecialWeaponStats = specialWinStats.stream()
			.filter(s -> Objects.equals(s.getSpecialWeapon(), ownPlayer.getWeapon().getSpecialWeapon()))
			.findFirst()
			.orElse(null);

		if (ownUsedSpecialWeaponStats == null) {
			return;
		}

		final var ownSpecialWeaponWinsAtStreamStart = specialWinStatsAtStreamStart.stream()
			.filter(s -> Objects.equals(s.getSpecialWeapon(), ownPlayer.getWeapon().getSpecialWeapon()))
			.findFirst()
			.map(SpecialWinCount::getWinCount)
			.orElse(0);

		// Stream Stats
		final var totalWins = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("WIN"))
			.count();
		final var totalDefeats = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("LOSE"))
			.count();

		var headGear = gearRepository.findByName(parsedOwnPlayer.getHeadGear().getName());
		var clothesGear = gearRepository.findByName(parsedOwnPlayer.getClothingGear().getName());
		var shoesGear = gearRepository.findByName(parsedOwnPlayer.getShoesGear().getName());

		chunksGainedStream = chunkGainRepository.findByReceivedAtAfter(twitchBotClient.getWentLiveTime()).size();

		fullscreenStreamData = FullscreenStreamData.builder()
			.type(FullscreenStreamData.Type.VS)
			.last_game_end_time(lastGame.getPlayedTime().plusSeconds(lastGame.getDuration()).toEpochMilli() - lastGame.getPlayedTime().truncatedTo(ChronoUnit.DAYS).toEpochMilli())
			.general(FullscreenStreamData.GeneralStats.builder()
				.wins(totalWins)
				.defeats(totalDefeats)
				.sub_weapon_image(getResourceUrl(ownPlayer.getWeapon().getSubWeapon().getImage()))
				.sub_weapon_games(usedSubWeaponResultStats.getTotalGames())
				.sub_weapon_wins(usedSubWeaponResultStats.getTotalWins())
				.special_weapon_image(getSpecialWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getName()))
				.special_wins(ownUsedSpecialWeaponStats.getWinCount())
				.special_wins_gained(ownUsedSpecialWeaponStats.getWinCount() - ownSpecialWeaponWinsAtStreamStart)
				.anarchy_rank(history.getData().getPlayHistory().getUdemae())
				.current_power(getPowerStats(lastGame, ownPlayer, allGamesInStream, ownUsedWeaponStats))
				.weapon_power(ownUsedWeaponStats.getStats().getCurrentWeaponPowerOrder() != null ? ownUsedWeaponStats.getStats().getCurrentWeaponPowerOrder().getWeaponPower() : null)
				.x_zones(allXPowers.getZones())
				.x_tower(allXPowers.getTower())
				.x_rain(allXPowers.getRainmaker())
				.x_clams(allXPowers.getClams())
				.chunks_gained(chunksGainedStream)
				.build())
			.weapon(FullscreenStreamData.WeaponInfo.builder()
				.name(ownPlayer.getWeapon().getName())
				.image(getMainWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getName(), ownPlayer.getWeapon().getImage3D()))
				.stats(weaponResultStats)
				.game_count(totalGameCount)
				.stars(ownUsedWeaponStats.getStats().getLevel())
				.exp_change(expWeaponGain)
				.exp_now(ownWeaponExpNow)
				.exp_start_ratio(alreadyOwnedExpRatio)
				.exp_change_ratio(earnedExpStreamRatio)
				.exp_left_ratio(remainingExpRatio)
				.build())
			.clothing(FullscreenStreamData.ClothingData.builder()
				.head(FullscreenStreamData.ClothingInfo.builder()
					.name(ownPlayer.getHeadGear().getName())
					.image(getResourceUrl(ownPlayer.getHeadGear().getOriginalImage()))
					.stars(headGear
						.map(Splatoon3VsGear::getGearLevel)
						.orElse(0))
					.game_count(loadedSplatNetObjects.getHeadGameCount().get())
					.main_image(getResourceUrl(ownPlayer.getHeadGearMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getHeadGearSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))

					.current_exp(headGear
						.map(Splatoon3VsGear::getCurrentExperience)
						.orElse(0))
					.previous_exp(headGear
						.map(Splatoon3VsGear::getPreviousExperience)
						.orElse(0))
					.exp_goal(headGear
						.map(Splatoon3VsGear::getGoalExperience)
						.orElse(0))
					.current_exp_ratio(getGearCurrentExpRatio(headGear))
					.previous_exp_ratio(getGearPreviousExpRatio(headGear))
					.exp_goal_ratio(getGearRemainingExpRatio(headGear))
					.chunks_gained(getChunksGain(headGear))
					.build())
				.shirt(FullscreenStreamData.ClothingInfo.builder()
					.name(ownPlayer.getClothingGear().getName())
					.image(getResourceUrl(ownPlayer.getClothingGear().getOriginalImage()))
					.stars(clothesGear
						.map(Splatoon3VsGear::getGearLevel)
						.orElse(0))
					.game_count(loadedSplatNetObjects.getShirtGameCount().get())
					.main_image(getResourceUrl(ownPlayer.getClothingMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getClothingSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))

					.current_exp(clothesGear
						.map(Splatoon3VsGear::getCurrentExperience)
						.orElse(0))
					.previous_exp(clothesGear
						.map(Splatoon3VsGear::getPreviousExperience)
						.orElse(0))
					.exp_goal(clothesGear
						.map(Splatoon3VsGear::getGoalExperience)
						.orElse(0))
					.current_exp_ratio(getGearCurrentExpRatio(clothesGear))
					.previous_exp_ratio(getGearPreviousExpRatio(clothesGear))
					.exp_goal_ratio(getGearRemainingExpRatio(clothesGear))
					.chunks_gained(getChunksGain(clothesGear))
					.build())
				.shoes(FullscreenStreamData.ClothingInfo.builder()
					.name(ownPlayer.getShoesGear().getName())
					.image(getResourceUrl(ownPlayer.getShoesGear().getOriginalImage()))
					.stars(shoesGear
						.map(Splatoon3VsGear::getGearLevel)
						.orElse(0))
					.game_count(loadedSplatNetObjects.getShoesGameCount().get())
					.main_image(getResourceUrl(ownPlayer.getShoesMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getShoesSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))

					.current_exp(shoesGear
						.map(Splatoon3VsGear::getCurrentExperience)
						.orElse(0))
					.previous_exp(shoesGear
						.map(Splatoon3VsGear::getPreviousExperience)
						.orElse(0))
					.exp_goal(shoesGear
						.map(Splatoon3VsGear::getGoalExperience)
						.orElse(0))
					.current_exp_ratio(getGearCurrentExpRatio(shoesGear))
					.previous_exp_ratio(getGearPreviousExpRatio(shoesGear))
					.exp_goal_ratio(getGearRemainingExpRatio(shoesGear))
					.chunks_gained(getChunksGain(shoesGear))
					.build())
				.build())
			.game(FullscreenStreamData.GameData.builder()
				.mode(lastGame.getMode().getName())
				.modeIcon(getModeIconResourceUrl(lastGame.getMode()))
				.rule(lastGame.getRule().getName())
				.ruleIcon(getRuleIconResourceUrl(lastGame))
				.stage(lastGame.getStage().getName())
				.teams(lastGame.getTeams().stream()
					.sorted((a, b) -> FullscreenStreamData.TeamData.Result.compare(mapResult(a.getJudgement()), mapResult(b.getJudgement())))
					.map(t -> FullscreenStreamData.TeamData.builder()
						.result(mapResult(t.getJudgement()))
						.result_str(mapResultStr(t.getScore(), t.getPaintRatio()))
						.color(String.format("#%02x%02x%02x%02x", (int) (255 * t.getInkColorR()), (int) (255 * t.getInkColorG()), (int) (255 * t.getInkColorB()), (int) (255 * t.getInkColorA())))
						.players(t.getTeamPlayers().stream()
							.map(tp -> FullscreenStreamData.PlayerData.builder()
								.name(tp.getName())
								.is_myself(tp.getIsMyself())
								.weapon_image(getResourceUrl(tp.getWeapon().getImage2D()))
								.special_weapon_image(getResourceUrl(tp.getWeapon().getSpecialWeapon().getImage()))
								.sub_weapon_image(getResourceUrl(tp.getWeapon().getSubWeapon().getImage()))
								.head_main_image(getResourceUrl(tp.getHeadGearMainAbility().getImage()))
								.shirt_main_image(getResourceUrl(tp.getClothingMainAbility().getImage()))
								.shoes_main_image(getResourceUrl(tp.getShoesMainAbility().getImage()))
								.kills(Stream.of(Optional.ofNullable(tp.getKills()), Optional.ofNullable(tp.getAssists()))
									.flatMap(Optional::stream)
									.reduce((kills, assists) -> kills - assists)
									.orElse(null))
								.assists(tp.getAssists())
								.deaths(tp.getDeaths())
								.specials(tp.getSpecials())
								.paint(tp.getPaint())
								.number_of_games(Optional.ofNullable(loadedSplatNetObjects.getPlayerMatchupNumbers().getOrDefault(tp.getPlayerId(), null)).filter(l -> l >= 0).orElse(null))
								.build())
							.collect(Collectors.toList()))
						.build())
					.collect(Collectors.toList()))
				.build())
			.map_stats(loadedSplatNetObjects.getStageWins())
			.build();
	}

	private FullscreenStreamData.PowerStats getPowerStats(Splatoon3VsResult lastGame, Splatoon3VsResultTeamPlayer ownPlayer, List<Splatoon3VsResult> allGamesInStream, Weapon ownUsedWeaponStats) {
		if (lastGame.isHasPower()) {
			var powerType = choosePowerType(lastGame);

			var xPowers = Optional.<S3XPowerDownloader.Powers>empty();
			Double currentXPowers = null;
			if (powerType == PowerType.X) {
				xPowers = loadedSplatNetObjects.getXPowers();

				if (xPowers.isEmpty()) {
					return null;
				}

				currentXPowers = getXPowerForRule(lastGame.getRule(), xPowers.get());
			}

			var allGamesToSearchForPower = List.<Splatoon3VsResult>of();

			if (powerType == PowerType.X) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()) && Objects.equals(g.getRule(), lastGame.getRule()))
					.collect(Collectors.toList());
			} else if (powerType == PowerType.SERIES) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()))
					.filter(g -> Objects.equals(getOwnPlayer(g).getWeapon(), ownPlayer.getWeapon()))
					.collect(Collectors.toList());
			} else if (powerType == PowerType.OPEN || powerType == PowerType.CHALLENGE) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getRotation(), lastGame.getRotation()))
					.collect(Collectors.toList());
			} else {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()))
					.collect(Collectors.toList());
			}

			final var secondToLastGame = allGamesToSearchForPower.stream()
				.filter(g -> g.getId() < lastGame.getId())
				.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime))
				.orElse(null);

			return FullscreenStreamData.PowerStats.builder()
				.mode_image(getModeIconResourceUrl(lastGame.getMode()))
				.rule_image(getRuleImageUrl(powerType, IconBadgeNames.RULES, lastGame.getRule().getName(), ownPlayer.getWeapon().getName(), ownPlayer.getWeapon().getImage3D()))
				.power_current(powerType == PowerType.X
					? currentXPowers
					: lastGame.getPower())
				.power_change(powerType == PowerType.X
					? (currentXPowers != null && lastGame.getPower() != null) ? currentXPowers - lastGame.getPower() : null
					: (lastGame.getPower() != null && secondToLastGame != null && secondToLastGame.getPower() != null
					? lastGame.getPower() - secondToLastGame.getPower()
					: null))
				.power_max(
					powerType == PowerType.X
						? null // TODO this would be nice to have
						: (
						powerType == PowerType.SERIES
							? Optional.ofNullable(ownUsedWeaponStats.getStats()).map(Stats::getMaxWeaponPower).orElse(null)
							:
							allGamesToSearchForPower.stream()
								.map(Splatoon3VsResult::getPower)
								.filter(Objects::nonNull)
								.max(Double::compare)
								.orElse(null))
				)
				.build();
		}

		return null;
	}

	private Splatoon3VsResultTeamPlayer getOwnPlayer(Splatoon3VsResult result) {
		return result.getTeams().stream()
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.orElseThrow();
	}

	private String shortenModeName(String modeName) {
		switch (modeName) {
			case "Regular Battle":
				return "TW";
			case "Anarchy Series":
				return "Series";
			case "Anarchy Open":
				return "Open";
			case "X Battle":
				return "X";
			case "Challenge":
				return "Challenge";
			case "Splatfest Open":
				return "SF Open";
			case "Splatfest Pro":
				return "SF Pro";
			case "Splatfest Tricolor":
				return "Tricolor";
			case "Private Battle":
				return "PBs";
			default:
				return "Total";
		}
	}


	private StreamData.TeamResult buildTeamResult(Splatoon3VsResultTeam team, double totalPointsSum) {
		if (team == null) {
			return null;
		}

		var points = team.getScore() != null
			? team.getScore()
			: team.getPaintRatio() * 100;

		return StreamData.TeamResult.builder()
			.color(String.format("#%02x%02x%02x%02x", (int) (255 * team.getInkColorR()), (int) (255 * team.getInkColorG()), (int) (255 * team.getInkColorB()), (int) (255 * team.getInkColorA())))
			.result(team.getJudgement() != null ? team.getJudgement() : "DRAW")
			.result_points(team.getScore() != null ? String.format("%d p", team.getScore()) : String.format("%.1f %%", team.getPaintRatio() * 100))
			.result_ratio((long) (points * 100 / totalPointsSum))
			.build();
	}

	private Double getXPowerForRule(Splatoon3VsRule rule, S3XPowerDownloader.Powers powers) {
		if (rule.getId() == 2) {
			return powers.getZones();
		}
		if (rule.getId() == 3) {
			return powers.getTower();
		}
		if (rule.getId() == 4) {
			return powers.getClams();
		}
		if (rule.getId() == 5) {
			return powers.getRainmaker();
		}

		return null;
	}

	private String getModeIconResourceUrl(Splatoon3VsMode mode) {
		if (mode.getId() == 1) {
			return "/img/regular-battle.svg";
		}

		if (mode.getId() == 2) {
			return getBadgeIconResourceUrl(IconBadgeNames.ANARCHY_SERIES);
		}

		if (mode.getId() == 3) {
			return getBadgeIconResourceUrl(IconBadgeNames.ANARCHY_OPEN);
		}

		if (mode.getId() == 4) {
			return getBadgeIconResourceUrl(IconBadgeNames.X_BATTLE);
		}

		if (mode.getId() == 5) {
			return getBadgeIconResourceUrl(IconBadgeNames.CHALLENGE);
		}

		if (mode.getId() == 6 || mode.getId() == 7) {
			return getBadgeIconResourceUrl(IconBadgeNames.SPLATFEST_TWO_TEAMS);
		}

		if (mode.getId() == 8) {
			return "/img/tricolor-turf-war.svg";
		}

		if (mode.getId() == 9) {
			return "/img/private-battle.svg";
		}

		return "";
	}

	private String getRuleIconResourceUrl(Splatoon3VsResult game) {
		if (game.getRule().getId() <= 5) {
			return getBadgeIconResourceUrl(IconBadgeNames.RULES, game.getRule().getName());
		}

		if (game.getRule().getId() == 6) {
			var ownTeam = game.getTeams().stream()
				.filter(t -> t.getTeamPlayers().stream().anyMatch(Splatoon3VsResultTeamPlayer::getIsMyself))
				.findFirst();

			if (ownTeam.isEmpty()) {
				return "";
			} else if (ownTeam.get().getTeamPlayers().size() == 2) {
				return getBadgeIconResourceUrl(IconBadgeNames.RULES, game.getRule().getName(), "Attacker");
			} else {
				return getBadgeIconResourceUrl(IconBadgeNames.RULES, game.getRule().getName(), "Defender");
			}
		}

		return "";
	}

	private String getRuleImageUrl(PowerType powerType, IconBadgeNames icon, @Nullable String ruleName, @NonNull String mainWeaponName, Image alternativeImage) {
		if (powerType == PowerType.SERIES) {
			return getMainWeaponBadgeIconResourceUrl(mainWeaponName, alternativeImage);
		} else {
			return getBadgeIconResourceUrl(icon, ruleName);
		}
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon) {
		return getBadgeIconResourceUrl(icon, null, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName) {
		return getBadgeIconResourceUrl(icon, ruleName, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName, @Nullable String filter) {
		return icon.getBadgeNames().stream()
			.filter(bn -> filter == null || bn.contains(filter))
			.filter(bn -> ruleName == null || bn.contains(ruleName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse("");
	}

	private String getMainWeaponBadgeIconResourceUrl(@NonNull String mainWeaponName, Image alternativeImage) {
		return IconBadgeNames.MAIN.getBadgeNames().stream()
			.map(bn -> String.format(bn, mainWeaponName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse(getResourceUrl(alternativeImage));
	}

	private String getSpecialWeaponBadgeIconResourceUrl(@NonNull String specialName) {
		return IconBadgeNames.SPECIAL.getBadgeNames().stream()
			.map(bn -> String.format(bn, specialName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse("");
	}

	private String getResourceUrl(Image image) {
		imageService.ensureImageIsDownloaded(image);

		return Optional.ofNullable(image.getFilePath())
			.map(url -> url.replace("./resources/prod/", "/splatnet3/"))
			.orElse("");
	}

	private int getChunksGain(Optional<Splatoon3VsGear> gear) {
		if (gear.isEmpty() || gear.get().getPreviousExperience() <= gear.get().getCurrentExperience()) {
			return 0;
		}

		return 1;
	}

	private int getGearPreviousExpRatio(Optional<Splatoon3VsGear> gear) {
		if (gear.isEmpty()) {
			return 0;
		}

		var g = gear.get();

		if (g.getPreviousExperience() <= g.getCurrentExperience()) {
			return g.getPreviousExperience() * 100 / g.getGoalExperience();
		} else {
			return 0;
		}
	}

	private int getGearCurrentExpRatio(Optional<Splatoon3VsGear> gear) {
		if (gear.isEmpty()) {
			return 0;
		}

		var g = gear.get();

		if (g.getPreviousExperience() <= g.getCurrentExperience()) {
			return (g.getCurrentExperience() - g.getPreviousExperience()) * 100 / g.getGoalExperience();
		} else {
			return g.getCurrentExperience() * 100 / g.getGoalExperience();
		}
	}

	private int getGearRemainingExpRatio(Optional<Splatoon3VsGear> gear) {
		if (gear.isEmpty()) {
			return 100;
		}

		return 100 - getGearPreviousExpRatio(gear) - getGearCurrentExpRatio(gear);
	}

	private String mapResultStr(Integer score, Double paintRatio) {
		if (score != null) {
			return String.format("%d p", score);
		} else if (paintRatio != null) {
			return String.format("%.1f %%", paintRatio * 100.0);
		}

		return "???";
	}

	private FullscreenStreamData.TeamData.Result mapResult(String judgement) {
		if ("WIN".equalsIgnoreCase(judgement)) {
			return FullscreenStreamData.TeamData.Result.WIN;
		} else if ("LOSE".equalsIgnoreCase(judgement)) {
			return FullscreenStreamData.TeamData.Result.LOSE;
		}

		return FullscreenStreamData.TeamData.Result.SUPPORT;
	}

	private int getExpGoal(Integer level) {
		var expGoal = 0;

		switch (level) {
			case 1: {
				expGoal = 25_000;
				break;
			}
			case 2: {
				expGoal = 60_000;
				break;
			}
			case 3: {
				expGoal = 160_000;
				break;
			}
			case 4: {
				expGoal = 1_160_000;
				break;
			}
			case 5: {
				expGoal = 2_000_000;
				break;
			}
			case 6: {
				expGoal = 3_000_000;
				break;
			}
			case 7: {
				expGoal = 4_000_000;
				break;
			}
			case 8: {
				expGoal = 5_000_000;
				break;
			}
			case 9:
			case 10: {
				expGoal = 6_000_000;
				break;
			}
			case 0:
			default: {
				expGoal = 5_000;
				break;
			}
		}

		return expGoal;
	}

	private int getWeaponExp(Integer level, Integer expToLevelUp) {
		var currentExp = 0;

		switch (level) {
			case 1: {
				currentExp = 25_000 - expToLevelUp;
				break;
			}
			case 2: {
				currentExp = 60_000 - expToLevelUp;
				break;
			}
			case 3: {
				currentExp = 160_000 - expToLevelUp;
				break;
			}
			case 4: {
				currentExp = 1_160_000 - expToLevelUp;
				break;
			}
			case 5: {
				currentExp = 2_000_000 - expToLevelUp;
				break;
			}
			case 6: {
				currentExp = 3_000_000 - expToLevelUp;
				break;
			}
			case 7: {
				currentExp = 4_000_000 - expToLevelUp;
				break;
			}
			case 8: {
				currentExp = 5_000_000 - expToLevelUp;
				break;
			}
			case 9: {
				currentExp = 6_000_000 - expToLevelUp;
				break;
			}
			case 10: {
				currentExp = 6_000_000;
				break;
			}
			case 0:
			default: {
				currentExp = 5_000 - expToLevelUp;
				break;
			}
		}

		return currentExp;
	}

	private PowerType choosePowerType(Splatoon3VsResult lastGame) {
		var powerType = PowerType.UNKNOWN;

		switch (lastGame.getMode().getApiMode()) {
			case "X_MATCH":
				powerType = PowerType.X;
				break;
			case "BANKARA":
				if (lastGame.getMode().getApiModeDistinction().equals("CHALLENGE")) {
					powerType = PowerType.SERIES;
				} else {
					powerType = PowerType.OPEN;
				}
				break;
			case "LEAGUE":
				powerType = PowerType.CHALLENGE;
				break;
			default:
				break;
		}

		return powerType;
	}

	private enum PowerType {
		OPEN,
		SERIES,
		X,
		CHALLENGE,
		UNKNOWN
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("StatsFullscreenFiller_run")
			.prioritized(true)
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::run)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

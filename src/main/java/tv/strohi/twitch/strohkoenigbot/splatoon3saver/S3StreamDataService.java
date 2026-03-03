package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.time.StopWatch;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultTeamPlayerRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsStageRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStatsWithRule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.FullscreenStreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.IconBadgeNames;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.StreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.HistoryResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Gear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Stats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3StreamDataService implements ScheduledService {
	private final TwitchBotClient twitchBotClient;
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final ObjectMapper objectMapper;
	private final S3ApiQuerySender apiQuerySender;

	private final S3SpecialWeaponWinStatsDownloader specialWeaponWinStatsDownloader;
	private final S3WeaponStatsDownloader weaponStatsDownloader;
	private final S3XPowerDownloader xPowerDownloader;
	private final S3GearDownloader gearDownloader;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3BadgeRepository badgeRepository;
	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3VsResultTeamPlayerRepository resultTeamPlayerRepository;
	private final Splatoon3VsStageRepository stageRepository;
	private final Splatoon3VsWeaponRepository weaponRepository;

	private final ImageService imageService;

	@Getter
	private StreamData streamData = StreamData.empty();

	@Getter
	private FullscreenStreamData fullscreenStreamData = FullscreenStreamData.empty();

	private Instant newestFoundGameStartTime = null;
	private List<SpecialWinCount> specialWinStatsAtStreamStart = null;
	private Weapon[] weaponStatsAtStreamStart = null;
	private List<OwnUsedWeaponStatsWithWeapon> ownUsedWeaponWinStatsAtStart = null;
	private List<StageWinStatsWithRule> stageResultStatsAtStart = null;

	private void refreshStreamData() {
		logIfDebug("S3StreamDataService: running refresh method");

		if (twitchBotClient.getWentLiveTime() == null) {
			streamData = StreamData.empty();
			fullscreenStreamData = FullscreenStreamData.empty();
			newestFoundGameStartTime = null;
			specialWinStatsAtStreamStart = null;
			weaponStatsAtStreamStart = null;
			ownUsedWeaponWinStatsAtStart = null;
			stageResultStatsAtStart = null;
			logIfDebug("S3StreamDataService: channel is offline");
			return;
		}

		var stopWatch = new StopWatch();
		var previousStopWatchTime = 0L;
		var stoppedTimeStrs = new ArrayList<String>();

		stopWatch.start();

		if (weaponStatsAtStreamStart == null) {
			logIfDebug("S3StreamDataService: weaponStatsAtStreamStart refresh");
			weaponStatsAtStreamStart = weaponStatsDownloader.downloadWeaponStats().orElse(null);
			stopWatch.split();
			stoppedTimeStrs.add(String.format("- weaponStatsDownloader.downloadWeaponStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
			previousStopWatchTime = stopWatch.getSplitTime();
		}


		if (specialWinStatsAtStreamStart == null) {
			logIfDebug("S3StreamDataService: specialWinStatsAtStreamStart refresh");
			specialWinStatsAtStreamStart = specialWeaponWinStatsDownloader.downloadSpecialWeaponStats().orElse(null);
			stopWatch.split();
			stoppedTimeStrs.add(String.format("- specialWeaponWinStatsDownloader.downloadSpecialWeaponStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
			previousStopWatchTime = stopWatch.getSplitTime();
		}


		if (ownUsedWeaponWinStatsAtStart == null) {
			ownUsedWeaponWinStatsAtStart = weaponRepository.getWeaponResultStatsForAllWeapons();
			stopWatch.split();
			stoppedTimeStrs.add(String.format("- weaponRepository.getWeaponResultStatsForAllWeapons `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
			previousStopWatchTime = stopWatch.getSplitTime();
		}


		if (stageResultStatsAtStart == null) {
			stageResultStatsAtStart = stageRepository.findAllStageWinStats();
			stopWatch.split();
			stoppedTimeStrs.add(String.format("- stageRepository.findAllStageWinStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
			previousStopWatchTime = stopWatch.getSplitTime();
		}


		final var allGamesInStream = resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc(twitchBotClient.getWentLiveTime());
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		if (allGamesInStream.isEmpty()) {
			streamData = StreamData.empty();
			newestFoundGameStartTime = null;
			logIfDebug("S3StreamDataService: no games found");
			return;
		}

		final var lastGame = allGamesInStream.get(allGamesInStream.size() - 1);

		if (newestFoundGameStartTime != null && newestFoundGameStartTime.equals(lastGame.getPlayedTime())) {
			// nothing to refresh
			logIfDebug("S3StreamDataService: nothing to refresh");
			return;
		}

		newestFoundGameStartTime = lastGame.getPlayedTime();

		final var ownPlayer = lastGame.getTeams().stream()
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.orElseThrow();

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- before Threads `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		var statData = LoadedSplatNetObjects.builder().build();

		var weaponDownloadThread = new Thread(() -> {
			final var threadStopWatch = new StopWatch();
			threadStopWatch.start();

			weaponStatsDownloader.downloadWeaponStats().ifPresent(statData::setWeapons);

			threadStopWatch.stop();
			stoppedTimeStrs.add(String.format("- Thread `weaponDownloadThread`: Finished after `%s ms`", threadStopWatch.getTime()));
		});
		var xPowerDownloadThread = new Thread(() -> {
			final var threadStopWatch = new StopWatch();
			threadStopWatch.start();

			xPowerDownloader.downloadXPowers().ifPresent(statData::setXPowers);

			threadStopWatch.stop();
			stoppedTimeStrs.add(String.format("- Thread `xPowerDownloadThread`: Finished after `%s ms`", threadStopWatch.getTime()));
		});
		var historyDownloadThread = new Thread(() -> {
			final var threadStopWatch = new StopWatch();
			threadStopWatch.start();

			try {
				var historyResponse = apiQuerySender.queryS3Api(accountRepository.findByIsMainAccount(true).stream().findFirst().orElseThrow(), S3RequestKey.History);
				statData.setParsedHistory(objectMapper.readValue(historyResponse, HistoryResult.class));
			} catch (Exception e) {
				exceptionLogger.logExceptionAsAttachment(log, "S3StreamDataService could not download History", e);
			}

			threadStopWatch.stop();
			stoppedTimeStrs.add(String.format("- Thread `historyDownloadThread`: Finished after `%s ms`", threadStopWatch.getTime()));
		});
		var choresThread = new Thread(() -> {
			final var threadStopWatch = new StopWatch();
			threadStopWatch.start();

			try {
				statData.setParsedLastGame(objectMapper.readValue(lastGame.getShortenedJson(), BattleResult.class));
			} catch (Exception e) {
				exceptionLogger.logExceptionAsAttachment(log, "S3StreamDataService could not parse original result from JSON", e);
			}

			statData.stageWins = (lastGame.getMode().getId() != 9L
				? Stream.of(lastGame.getRotation().getStage1(), lastGame.getRotation().getStage2())
				: Stream.of(lastGame.getStage()))
				.map(st -> FullscreenStreamData.MapData.builder()
					.name(st.getName())
					.image(getResourceUrl(st.getImage()))
					.stats(stageRepository.findStageWinStats(st.getId(), lastGame.getRule().getId()).stream()
						.map(sws -> new FullscreenStreamData.KeyWinDefeatRate(shortenModeName(sws.getModeName()), FullscreenStreamData.WinDefeatRate.builder()
							.wins(sws.getWinCount())
							.wins_gained(sws.getWinCount() - stageResultStatsAtStart.stream()
								.filter(s -> Objects.equals(s.getMapName(), sws.getMapName()) && Objects.equals(s.getModeName(), sws.getModeName()) && Objects.equals(s.getRuleName(), lastGame.getRule().getName()))
								.findFirst()
								.map(StageWinStatsWithRule::getWinCount)
								.orElse(sws.getWinCount()))
							.defeats(sws.getDefeatCount())
							.defeats_gained(sws.getDefeatCount() - stageResultStatsAtStart.stream()
								.filter(s -> Objects.equals(s.getMapName(), sws.getMapName()) && Objects.equals(s.getModeName(), sws.getModeName()) && Objects.equals(s.getRuleName(), lastGame.getRule().getName()))
								.findFirst()
								.map(StageWinStatsWithRule::getDefeatCount)
								.orElse(sws.getDefeatCount()))
							.winrate(100.0 * sws.getWinCount() / (sws.getWinCount() + sws.getDefeatCount()))
							.build()))
						.collect(Collectors.toList()))
					.build())
				.collect(Collectors.toList());

			specialWeaponWinStatsDownloader.downloadSpecialWeaponStats().ifPresent(statData::setSpecialWinCounts);

			statData.setHeadGameCount(resultTeamPlayerRepository.getGameCountOfOwnHeadGearId(ownPlayer.getHeadGear().getId()));
			statData.setShirtGameCount(resultTeamPlayerRepository.getGameCountOfOwnClothingGearId(ownPlayer.getClothingGear().getId()));
			statData.setShoesGameCount(resultTeamPlayerRepository.getGameCountOfOwnShoesGearId(ownPlayer.getShoesGear().getId()));

			statData.setPlayerMatchupNumbers(
				lastGame.getTeams().stream()
					.flatMap(t -> t.getTeamPlayers().stream())
					.collect(Collectors.toMap(
						Splatoon3VsResultTeamPlayer::getPlayerId,
						(Splatoon3VsResultTeamPlayer tp) -> tp.getIsMyself() ? -1L : resultTeamPlayerRepository.getGameCountsWithPlayer(tp.getPlayerId())
					)));

			threadStopWatch.stop();
			stoppedTimeStrs.add(String.format("- Thread `choresThread`: Finished after `%s ms`", threadStopWatch.getTime()));
		});

		try {
			weaponDownloadThread.start();
			xPowerDownloadThread.start();
			historyDownloadThread.start();
			choresThread.start();

			weaponDownloadThread.join();
			xPowerDownloadThread.join();
			historyDownloadThread.join();
			choresThread.join();

			if (statData.containsEmptyField()) {
				logSender.sendLogsAsAttachment(log, "At least one field was not filled properly!", statData.toString());
				return;
			}
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "Error while running S3StreamDataService Threads", ex);
			return;
		}

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- after Threads `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

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

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- get Teams `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		// Weapon Stats
		final var weaponStats = statData.weapons;
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- weaponStatsDownloader.downloadWeaponStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();
		if (weaponStats == null) {
			logIfDebug("S3StreamDataService: weaponStats null");
			return;
		}

		final var specialWinStats = statData.specialWinCounts;
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- specialWeaponWinStatsDownloader.downloadSpecialWeaponStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();
		if (specialWinStats == null) {
			logIfDebug("S3StreamDataService: specialWinStats null");
			return;
		}

		// Game Stats
		final var ownUsedWeaponStats = Arrays.stream(weaponStats)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStats == null) {
			logIfDebug("S3StreamDataService: ownUsedWeaponStats null");
			return;
		}

		final var ownUsedWeaponStatsAtStart = Arrays.stream(weaponStatsAtStreamStart)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStatsAtStart == null) {
			logIfDebug("S3StreamDataService: ownUsedWeaponStatsAtStart null");
			return;
		}
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- ownUsedWeaponStatsAtStart `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

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
			logIfDebug("S3StreamDataService: ownUsedSpecialWeaponStats null");
			return;
		}

		final var ownSpecialWeaponWinsAtStreamStart = specialWinStatsAtStreamStart.stream()
			.filter(s -> Objects.equals(s.getSpecialWeapon(), ownPlayer.getWeapon().getSpecialWeapon()))
			.findFirst()
			.map(SpecialWinCount::getWinCount)
			.orElse(0);

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- ownSpecialWeaponWinsAtStreamStart `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		// Stream Stats
		final var totalWins = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("WIN"))
			.count();
		final var totalDefeats = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("LOSE"))
			.count();
		final var winRatio = totalWins * 100.0 / (Math.max(totalWins + totalDefeats, 1));
		final var defeatRatio = 100.0 - winRatio;
		final var totalPointsSum = ownTeam.getScore() != null
			? ownTeam.getScore() + opp1.getScore() + (opp2 != null ? opp2.getScore() : 0)
			: ownTeam.getPaintRatio() * 100 + opp1.getPaintRatio() * 100 + (opp2 != null ? opp2.getPaintRatio() * 100 : 0);

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- totalPointsSum `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		var streamDataBuilder = StreamData.prepare()
			.weapon_info(
				StreamData.WeaponInfo.builder()
					.image(getMainWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getName(), ownPlayer.getWeapon().getImage3D()))
					.sub_weapon_image(getResourceUrl(ownPlayer.getWeapon().getSubWeapon().getImage()))
					.special_weapon_image(getResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getImage()))
					.wins(ownUsedWeaponStats.getStats().getWin())
					.stars(ownUsedWeaponStats.getStats().getLevel())
					.exp_start(ownWeaponExpAtStart)
					.exp_change(expWeaponGain)
					.exp_now(ownWeaponExpNow)
					.exp_start_ratio(alreadyOwnedExpRatio)
					.exp_change_ratio(earnedExpStreamRatio)
					.exp_left_ratio(remainingExpRatio)
					.build())
			.abilities_info(StreamData.AbilitiesInfo.builder()
				.head(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getHeadGearMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getHeadGearSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shirt(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getClothingMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getClothingSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shoes(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getShoesMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getShoesSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.build())
			.team_stats(StreamData.MatchStats.builder()
				.own_team(buildTeamResult(ownTeam, totalPointsSum))
				.opp_1(buildTeamResult(opp1, totalPointsSum))
				.opp_2(Optional.ofNullable(opp2)
					.map(o -> buildTeamResult(o, totalPointsSum))
					.orElse(null))
				.build())
			.stream_stats(StreamData.StreamStats.builder()
				.wins(totalWins)
				.defeats(totalDefeats)
				.win_ratio(winRatio)
				.defeat_ratio(defeatRatio)
				.build())
			.game_stats(StreamData.GameStats.builder()
				.kills(ownPlayer.getKills() - ownPlayer.getAssists())
				.deaths(ownPlayer.getDeaths())
				.assists(ownPlayer.getAssists())
				.specials(ownPlayer.getSpecials())
				.paint(ownPlayer.getPaint())
				.build())
			.special_stats(StreamData.SpecialStats.builder()
				.image(getSpecialWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getName()))
				.wins(ownUsedSpecialWeaponStats.getWinCount())
				.gained(ownUsedSpecialWeaponStats.getWinCount() - ownSpecialWeaponWinsAtStreamStart)
				.build())
			.power_stats(null);

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- streamDataBuilder `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		if (lastGame.isHasPower()) {
			final var isX = lastGame.getMode().getApiMode().equals("X_MATCH");
			final var isAnarchySeries = lastGame.getMode().getApiMode().equals("BANKARA") && lastGame.getMode().getApiModeDistinction().equals("CHALLENGE");

			var xPowers = Optional.<S3XPowerDownloader.Powers>empty();
			Double currentXPowers = null;
			if (isX) {
				xPowers = Optional.of(statData.xPowers);

				if (xPowers.isEmpty()) {
					logIfDebug("## Error\n- S3StreamDataService could not load X Powers!");
					return;
				}

				currentXPowers = getXPowerForRule(lastGame.getRule(), xPowers.get());
			}

			final var allGamesFromRotation = allGamesInStream.stream()
				.filter(g -> Objects.equals(g.getRotation(), lastGame.getRotation()))
				.collect(Collectors.toList());
			final var secondToLastGame = allGamesFromRotation.stream()
				.filter(g -> g.getId() < lastGame.getId())
				.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime))
				.orElse(null);


			streamDataBuilder = streamDataBuilder
				.power_stats(StreamData.PowerStats.builder()
					.mode_image(getModeIconResourceUrl(lastGame.getMode()))
					.rule_image(getBadgeIconResourceUrl(IconBadgeNames.RULES, lastGame.getRule().getName()))
					.power_current(isX
						? currentXPowers
						: lastGame.getPower())
					.power_change(isX
						? (currentXPowers != null && lastGame.getPower() != null) ? currentXPowers - lastGame.getPower() : null
						: (lastGame.getPower() != null && secondToLastGame != null && secondToLastGame.getPower() != null
						? lastGame.getPower() - secondToLastGame.getPower()
						: null))
					.power_max(
						isX
							? null
							: (
							isAnarchySeries
								? Optional.ofNullable(ownUsedWeaponStats.getStats()).map(Stats::getMaxWeaponPower).orElse(null)
								:
								allGamesFromRotation.stream()
									.map(Splatoon3VsResult::getPower)
									.filter(Objects::nonNull)
									.max(Double::compare)
									.orElse(null))
					)
					.build());

			stopWatch.split();
			stoppedTimeStrs.add(String.format("- if lastGame.isHasPower `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
			previousStopWatchTime = stopWatch.getSplitTime();
		}

		var result = streamDataBuilder.build();
		if (100 > (result.getTeam_stats().getOwn_team().getResult_ratio()
			+ result.getTeam_stats().getOpp_1().getResult_ratio()
			+ (result.getTeam_stats().getOpp_2() != null ? result.getTeam_stats().getOpp_2().getResult_ratio() : 0))) {
			var difference = 100 - (result.getTeam_stats().getOwn_team().getResult_ratio()
				+ result.getTeam_stats().getOpp_1().getResult_ratio()
				+ (result.getTeam_stats().getOpp_2() != null ? result.getTeam_stats().getOpp_2().getResult_ratio() : 0));

			result.getTeam_stats().getOwn_team().setResult_ratio(result.getTeam_stats().getOwn_team().getResult_ratio() + difference);
		}

		streamData = result;

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- S3StreamDataService: streamData refreshed `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		logIfDebug("S3StreamDataService: streamData refreshed");

		final var parsedOriginalResult = statData.parsedLastGame;
		final var history = statData.parsedHistory;

		var parsedOwnPlayer = parsedOriginalResult.getData().getVsHistoryDetail().getMyTeam().getPlayers().stream()
			.filter(Player::getIsMyself)
			.findFirst()
			.orElse(parsedOriginalResult.getData().getVsHistoryDetail().getPlayer());
		var allXPowers = Optional.of(statData.xPowers).orElse(new S3XPowerDownloader.Powers(null, null, null, null));
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- xPowerDownloader.downloadXPowers `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();
		var allWeaponResultStats = weaponRepository.getWeaponResultStats(ownPlayer.getWeapon().getId());
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- weaponRepository.getWeaponResultStats `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();
		var weaponResultStats = allWeaponResultStats.stream()
			.map(w -> new FullscreenStreamData.KeyWinDefeatRate(shortenModeName(w.getModeName()), FullscreenStreamData.WinDefeatRate.builder()
				.wins(w.getTotalWins())
				.wins_gained(w.getTotalWins() - ownUsedWeaponWinStatsAtStart.stream()
					.filter(was -> was.getWeaponId() == ownPlayer.getWeapon().getId() && Objects.equals(was.getModeName(), w.getModeName()))
					.findFirst()
					.map(OwnUsedWeaponStatsWithWeapon::getTotalWins)
					.orElse(w.getTotalWins()))
				.defeats(w.getTotalDefeats())
				.defeats_gained(w.getTotalDefeats() - ownUsedWeaponWinStatsAtStart.stream()
					.filter(was -> was.getWeaponId() == ownPlayer.getWeapon().getId() && Objects.equals(was.getModeName(), w.getModeName()))
					.findFirst()
					.map(OwnUsedWeaponStatsWithWeapon::getTotalDefeats)
					.orElse(w.getTotalDefeats()))
				.winrate(w.getWinRate())
				.build())).collect(Collectors.toCollection(ArrayList::new));
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- weaponResultStats = allWeaponResultStats.stream() `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		var totalWeaponWinStats = new FullscreenStreamData.KeyWinDefeatRate("Total", allWeaponResultStats.stream()
			.map(w -> FullscreenStreamData.WinDefeatRate.builder()
				.wins(w.getTotalWins())
				.wins_gained(w.getTotalWins() - ownUsedWeaponWinStatsAtStart.stream()
					.filter(was -> was.getWeaponId() == ownUsedWeaponStats.getWeaponId() && Objects.equals(was.getModeName(), w.getModeName()))
					.findFirst()
					.map(OwnUsedWeaponStatsWithWeapon::getTotalWins)
					.orElse(w.getTotalWins()))
				.defeats(w.getTotalDefeats())
				.defeats_gained(w.getTotalDefeats() - ownUsedWeaponWinStatsAtStart.stream()
					.filter(was -> was.getWeaponId() == ownUsedWeaponStats.getWeaponId() && Objects.equals(was.getModeName(), w.getModeName()))
					.findFirst()
					.map(OwnUsedWeaponStatsWithWeapon::getTotalDefeats)
					.orElse(w.getTotalDefeats()))
				.winrate(w.getWinRate())
				.build())
			.reduce((a, b) ->
				FullscreenStreamData.WinDefeatRate.builder()
					.wins(a.getWins() + b.getWins())
					.wins_gained(a.getWins_gained() + b.wins_gained)
					.defeats(a.getDefeats() + b.getDefeats())
					.defeats_gained(a.getDefeats_gained() + b.getDefeats_gained())
					.winrate(100.0 * (a.getWins() + b.getWins()) / (a.getWins() + b.getWins() + a.getDefeats() + b.getDefeats()))
					.build())
			.get());
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- totalWeaponWinStats = new FullscreenStreamData.KeyWinDefeatRate `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		weaponResultStats.add(totalWeaponWinStats);

		var totalGameCount = totalWeaponWinStats.win_defeat_rate.wins + totalWeaponWinStats.win_defeat_rate.defeats;

		if (gearDownloader.getCachedGears().isEmpty()) {
			return;
		}

		var downloadedGears = gearDownloader.getCachedGears();
		stopWatch.split();
		stoppedTimeStrs.add(String.format("- gearDownloader.downloadGears `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		previousStopWatchTime = stopWatch.getSplitTime();

		fullscreenStreamData = FullscreenStreamData.builder()
			.type(FullscreenStreamData.Type.VS)
			.last_game_end_time(lastGame.getPlayedTime().plusSeconds(lastGame.getDuration()).toEpochMilli() - lastGame.getPlayedTime().truncatedTo(ChronoUnit.DAYS).toEpochMilli())
			.general(FullscreenStreamData.GeneralStats.builder()
				.wins(totalWins)
				.defeats(totalDefeats)
				.special_weapon_image(getSpecialWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getName()))
				.special_wins(ownUsedSpecialWeaponStats.getWinCount())
				.special_wins_gained(ownUsedSpecialWeaponStats.getWinCount() - ownSpecialWeaponWinsAtStreamStart)
				.anarchy_rank(history.getData().getPlayHistory().getUdemae())
				.weapon_power(ownUsedWeaponStats.getStats().getCurrentWeaponPowerOrder() != null ? ownUsedWeaponStats.getStats().getCurrentWeaponPowerOrder().getWeaponPower() : null)
				.x_zones(allXPowers.getZones())
				.x_tower(allXPowers.getTower())
				.x_rain(allXPowers.getRainmaker())
				.x_clams(allXPowers.getClams())
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
					.stars(downloadedGears.stream()
						.flatMap(g -> Arrays.stream(g.getHead()))
						.filter(g -> Objects.equals(parsedOwnPlayer.getHeadGear().getName(), g.getName()))
						.map(Gear::getRarity)
						.findFirst()
						.orElse(0))
					.game_count(statData.getHeadGameCount())
					.main_image(getResourceUrl(ownPlayer.getHeadGearMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getHeadGearSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shirt(FullscreenStreamData.ClothingInfo.builder()
					.name(ownPlayer.getClothingGear().getName())
					.image(getResourceUrl(ownPlayer.getClothingGear().getOriginalImage()))
					.stars(downloadedGears.stream()
						.flatMap(g -> Arrays.stream(g.getClothing()))
						.filter(g -> Objects.equals(parsedOwnPlayer.getClothingGear().getName(), g.getName()))
						.map(Gear::getRarity)
						.findFirst()
						.orElse(0))
					.game_count(statData.getShirtGameCount())
					.main_image(getResourceUrl(ownPlayer.getClothingMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getClothingSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shoes(FullscreenStreamData.ClothingInfo.builder()
					.name(ownPlayer.getShoesGear().getName())
					.image(getResourceUrl(ownPlayer.getShoesGear().getOriginalImage()))
					.stars(downloadedGears.stream()
						.flatMap(g -> Arrays.stream(g.getShoes()))
						.filter(g -> Objects.equals(parsedOwnPlayer.getShoesGear().getName(), g.getName()))
						.map(Gear::getRarity)
						.findFirst()
						.orElse(0))
					.game_count(statData.getShoesGameCount())
					.main_image(getResourceUrl(ownPlayer.getShoesMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getShoesSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.build())
			.game(FullscreenStreamData.GameData.builder()
				.teams(lastGame.getTeams().stream()
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
								.kills(tp.getKills() - tp.getAssists())
								.assists(tp.getAssists())
								.deaths(tp.getDeaths())
								.specials(tp.getSpecials())
								.paint(tp.getPaint())
								.number_of_games(Optional.ofNullable(statData.getPlayerMatchupNumbers().getOrDefault(tp.getPlayerId(), null)).filter(l -> l >= 0).orElse(null))
								.build())
							.collect(Collectors.toList()))
						.build())
					.collect(Collectors.toList()))
				.build())
			.map_stats(statData.stageWins)
			.build();

		stopWatch.split();
		stoppedTimeStrs.add(String.format("- fullscreenStreamData = FullscreenStreamData.builder() `%d ms` - Total time so far: `%d ms`", stopWatch.getSplitTime() - previousStopWatchTime, stopWatch.getSplitTime()));
		stoppedTimeStrs.add(String.format("- Total time: `%d ms`", stopWatch.getTime()));
		stopWatch.stop();

		log.info("finished S3StreamDataService reload");
		logIfDebug("# S3StreamDataService StopWatch times\n%s", stoppedTimeStrs.stream().reduce((a, b) -> String.format("%s\n%s", a, b)).orElse(""));
	}

	private String mapResultStr(Integer score, Double paintRatio) {
		if (score != null) {
			return String.format("%d p", score);
		} else if (paintRatio != null) {
			return String.format("%.1f %%", paintRatio);
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

		if (mode.getId() == 7) {
			return getBadgeIconResourceUrl(IconBadgeNames.SPLATFEST);
		}

		return "";
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon) {
		return getBadgeIconResourceUrl(icon, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName) {
		return icon.getBadgeNames().stream()
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

	private void logIfDebug(String message, Object... args) {
		var shouldLogConfig = configurationRepository.findByConfigName("S3StreamDataService_logDebugSwitch")
			.orElseGet(() -> configurationRepository.save(Configuration.builder()
				.configName("S3StreamDataService_logDebugSwitch")
				.configValue("false")
				.build()));

		if ("true".equalsIgnoreCase(shouldLogConfig.getConfigValue())) {
			logSender.sendLogs(log, message, args);
		} else {
			log.info(String.format(message, args));
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3StreamDataService_refreshStreamData")
			.schedule(TickSchedule.getScheduleString(13))
			.runnable(this::refreshStreamData)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	@Getter
	@Setter
	@Builder(toBuilder = true)
	@ToString
	private static class LoadedSplatNetObjects {
		private Weapon[] weapons;
		private S3XPowerDownloader.Powers xPowers;
		private List<SpecialWinCount> specialWinCounts;
		private HistoryResult parsedHistory;
		private BattleResult parsedLastGame;
		private List<FullscreenStreamData.MapData> stageWins;
		private Long headGameCount;
		private Long shirtGameCount;
		private Long shoesGameCount;
		private Map<Long, Long> playerMatchupNumbers;

		public boolean containsEmptyField() {
			return weapons == null
				|| xPowers == null
				|| specialWinCounts == null
				|| parsedHistory == null
				|| parsedLastGame == null
				|| stageWins == null
				|| headGameCount == null
				|| shirtGameCount == null
				|| shoesGameCount == null
				|| playerMatchupNumbers == null
				|| playerMatchupNumbers.isEmpty();
		}
	}
}

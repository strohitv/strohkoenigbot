package tv.strohi.twitch.strohkoenigbot.splatoon3saver.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.time.StopWatch;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.messaging.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultTeamPlayerRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsStageRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStatsWithRule;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.FullscreenStreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.HistoryResult;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class SplatNetObjectLoader implements ScheduledService {
	private final LoadedSplatNetObjects loadedSplatNetObjects;

	private final TwitchBotClient twitchBotClient;
	private final ExceptionLogger exceptionLogger;
	private final ImageService imageService;

	private final AccountRepository accountRepository;
	private final Splatoon3VsStageRepository stageRepository;
	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3VsResultTeamPlayerRepository resultTeamPlayerRepository;

	private final S3WeaponStatsDownloader weaponStatsDownloader;
	private final S3GearDownloader gearDownloader;
	private final S3XPowerDownloader xPowerDownloader;
	private final S3ApiQuerySender apiQuerySender;
	private final S3SpecialWeaponWinStatsDownloader specialWeaponWinStatsDownloader;

	private final ObjectMapper objectMapper;

	private final List<StageWinStatsWithRule> stageResultStatsAtStart = new ArrayList<>();

	private final Map<String, Boolean> threadRunStages = new HashMap<>();

	@Transactional
	public void run() {
		if (twitchBotClient.getWentLiveTime() == null) {
			loadedSplatNetObjects.reset();
			stageResultStatsAtStart.clear();
			loadedSplatNetObjects.getWeaponStatsAtStreamStart().clear();

			return;
		}

		if (stageResultStatsAtStart.isEmpty()) {
			stageResultStatsAtStart.addAll(stageRepository.findAllStageWinStats(twitchBotClient.getWentLiveTime()));
		}

		if (loadedSplatNetObjects.getWeaponStatsAtStreamStart().isEmpty()) {
			weaponStatsDownloader.downloadWeaponStats()
				.ifPresent(w -> loadedSplatNetObjects.getWeaponStatsAtStreamStart().addAll(List.of(w)));
		}

		try {
			if (threadRunStages.getOrDefault("weaponDownloadThread", true)) {
				threadRunStages.put("weaponDownloadThread", false);
				var weaponDownloadThread = new Thread(this::runWeaponDownload);
				weaponDownloadThread.start();
			}

			if (threadRunStages.getOrDefault("gearDownloadThread", true)) {
				threadRunStages.put("gearDownloadThread", false);
				var gearDownloadThread = new Thread(this::runGearDownload);
				gearDownloadThread.start();
			}

			if (threadRunStages.getOrDefault("xPowerDownloadThread", true)) {
				threadRunStages.put("xPowerDownloadThread", false);
				var xPowerDownloadThread = new Thread(this::runXPowerDownload);
				xPowerDownloadThread.start();
			}

			if (threadRunStages.getOrDefault("historyDownloadThread", true)) {
				threadRunStages.put("historyDownloadThread", false);
				var historyDownloadThread = new Thread(this::runHistoryDownload);
				historyDownloadThread.start();
			}
		} catch (Exception ex) {
			log.error(ex);
		}

		try {
			// run general chores (usually fast enough)
			runChores();
		} catch (CannotAcquireLockException ex) {
			exceptionLogger.logExceptionAsAttachment(log, "An Exception occurred because of synchronous repository access. It won't affect the run of the ScheduledService but please handle it!", ex);
		}
	}

	private void runWeaponDownload() {
		log.info("weaponDownloadThread started");
		final var threadStopWatch = new StopWatch();
		threadStopWatch.start();

		try {
			weaponStatsDownloader.downloadWeaponStats().ifPresent(weapons -> loadedSplatNetObjects.setWeapons(Optional.of(weapons)));
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader exception in weapon download thread", ex);
		}

		threadStopWatch.stop();
		log.info("`weaponDownloadThread` finished after `{}` seconds", threadStopWatch.getTime() / 1000.0);
		threadRunStages.put("weaponDownloadThread", true);
	}

	private void runGearDownload() {
		log.info("gearDownloadThread started");
		final var threadStopWatch = new StopWatch();
		threadStopWatch.start();

		try {
			gearDownloader.saveGears();
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader exception in gear download thread", ex);
		}

		threadStopWatch.stop();
		log.info("`gearDownloadThread` finished after `{}` seconds", threadStopWatch.getTime() / 1000.0);
		threadRunStages.put("gearDownloadThread", true);
	}

	private void runXPowerDownload() {
		log.info("xPowerDownloadThread started");
		final var threadStopWatch = new StopWatch();
		threadStopWatch.start();

		try {
			xPowerDownloader.downloadXPowers().ifPresent(xPowers -> loadedSplatNetObjects.setXPowers(Optional.of(xPowers)));
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader exception in x power download thread", ex);
		}

		threadStopWatch.stop();
		log.info("`xPowerDownloadThread` finished after `{}` seconds", threadStopWatch.getTime() / 1000.0);
		threadRunStages.put("xPowerDownloadThread", true);
	}

	private void runHistoryDownload() {
		log.info("historyDownloadThread started");
		final var threadStopWatch = new StopWatch();
		threadStopWatch.start();

		try {
			var historyResponse = apiQuerySender.queryS3Api(accountRepository.findByIsMainAccount(true).stream().findFirst().orElseThrow(), S3RequestKey.History);
			loadedSplatNetObjects.setParsedHistory(Optional.of(objectMapper.readValue(historyResponse, HistoryResult.class)));
		} catch (Exception e) {
			exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader exception in history download thread", e);
		}

		threadStopWatch.stop();
		log.info("`historyDownloadThread` finished after `{}` seconds", threadStopWatch.getTime() / 1000.0);
		threadRunStages.put("historyDownloadThread", true);
	}

	private void runChores() {
		log.info("chores Runner started");
		final var threadStopWatch = new StopWatch();
		threadStopWatch.start();

		final var allGamesInStream = resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc(twitchBotClient.getWentLiveTime());
		final var lastGame = (!allGamesInStream.isEmpty()) ? allGamesInStream.get(allGamesInStream.size() - 1) : null;

		if (lastGame == null) {
			log.info("chores running cancelled because lastGame was null");
			return;
		}

		final var ownPlayer = lastGame.getTeams().stream()
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.orElseThrow();

		var currentStageWins = (lastGame.getMode().getId() != 9L
			? Stream.of(lastGame.getRotation().getStage1(), lastGame.getRotation().getStage2())
			: Stream.of(lastGame.getStage()))
			.filter(Objects::nonNull)
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

		loadedSplatNetObjects.getStageWins().clear();
		loadedSplatNetObjects.getStageWins().addAll(currentStageWins);

		try {
			try {
				loadedSplatNetObjects.setParsedLastGame(Optional.of(objectMapper.readValue(lastGame.getShortenedJson(), BattleResult.class)));
			} catch (Exception e) {
				exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader could not parse original result from JSON", e);
			}

			specialWeaponWinStatsDownloader.downloadSpecialWeaponStats().ifPresent(wws -> {
				loadedSplatNetObjects.getSpecialWinCounts().clear();
				loadedSplatNetObjects.getSpecialWinCounts().addAll(wws);
			});

			loadedSplatNetObjects.setHeadGameCount(Optional.of(resultTeamPlayerRepository.getGameCountOfOwnHeadGearId(ownPlayer.getHeadGear().getId())));
			loadedSplatNetObjects.setShirtGameCount(Optional.of(resultTeamPlayerRepository.getGameCountOfOwnClothingGearId(ownPlayer.getClothingGear().getId())));
			loadedSplatNetObjects.setShoesGameCount(Optional.of(resultTeamPlayerRepository.getGameCountOfOwnShoesGearId(ownPlayer.getShoesGear().getId())));


			var playerMatchupNumbers =
				lastGame.getTeams().stream()
					.flatMap(t -> t.getTeamPlayers().stream())
					.collect(Collectors.toMap(
						Splatoon3VsResultTeamPlayer::getPlayerId,
						(Splatoon3VsResultTeamPlayer tp) -> tp.getIsMyself() ? -1L : resultTeamPlayerRepository.getGameCountsWithPlayer(tp.getPlayerId())
					));
			loadedSplatNetObjects.getPlayerMatchupNumbers().clear();
			loadedSplatNetObjects.getPlayerMatchupNumbers().putAll(playerMatchupNumbers);
		} catch (Exception ex) {
			exceptionLogger.logExceptionAsAttachment(log, "SplatNetObjectLoader exception in chores thread", ex);
		} finally {
			threadStopWatch.stop();
			log.info("`cores runner` finished after `{}` seconds", threadStopWatch.getTime() / 1000.0);
			threadRunStages.put("choresThread", true);
		}
	}

	private String getResourceUrl(Image image) {
		imageService.ensureImageIsDownloaded(image);

		return Optional.ofNullable(image.getFilePath())
			.map(url -> url.replace("./resources/prod/", "/splatnet3/"))
			.orElse("");
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

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("SplatNetObjectLoader_run")
			.prioritized(true)
			.schedule(CronSchedule.getScheduleString("*/12 * * * * *"))
			.runnable(this::run)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class Splatoon3PowerMigrator implements ScheduledService {
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3VsResultRepository resultRepository;

	private final ExceptionLogger exceptionLogger;
	private final LogSender logSender;

	private final ObjectMapper mapper;

	@Transactional
	public void addMissingPowers() {
		if (configurationRepository.findByConfigName("Splatoon3PowerExporter_addMissingPowers_finished").isPresent()) {
			return;
		}

		var totalGames = 0;
		var gamesWithPowerAdded = 0;

		var pageable = Pageable.ofSize(100);
		while (pageable.isPaged()) {
			var games = resultRepository.findAll(pageable);

			var gamesToSave = new ArrayList<Splatoon3VsResult>();

			for (var game : games) {
				try {
					totalGames += 1;

					var castedGameResult = mapper.readValue(game.getShortenedJson(), BattleResult.class)
						.getData()
						.getVsHistoryDetail();

					if (castedGameResult.getXMatch() != null) {
						// X Battle
						game.setHasPower(true);
						game.setPower(castedGameResult.getXMatch().getLastXPower());
					} else if (castedGameResult.getLeagueMatch() != null) {
						// Challenge
						game.setHasPower(true);
						game.setPower(castedGameResult.getLeagueMatch().getMyLeaguePower());
					} else if (castedGameResult.getVsMode().getId().equals("VnNNb2RlLTc=") && castedGameResult.getFestMatch() != null) {
						// Splatfest Pro
						game.setHasPower(true);
						game.setPower(castedGameResult.getFestMatch().getMyFestPower());
					} else if (castedGameResult.getVsMode().getId().equals("VnNNb2RlLTUx") && castedGameResult.getBankaraMatch().getBankaraPower() != null) {
						// Anarchy Open
						game.setHasPower(true);
						game.setPower(castedGameResult.getBankaraMatch().getBankaraPower().getPower());
					} else if (castedGameResult.getVsMode().getId().equals("VnNNb2RlLTI=")
						&& game.getPlayedTime().isAfter(LocalDate.of(2025, 6, 12).atStartOfDay().toInstant(ZoneOffset.UTC))
						&& castedGameResult.getBankaraMatch() != null) {
						// Anarchy Series
						game.setHasPower(true);
						game.setPower(castedGameResult.getBankaraMatch().getWeaponPower());
					}

					if (game.isHasPower()) {
						gamesWithPowerAdded += 1;
						gamesToSave.add(game);
					}
				} catch (JsonProcessingException e) {
					exceptionLogger.logExceptionAsAttachment(log, "Error during power fix game result parsing", e);
				}
			}

			resultRepository.saveAll(gamesToSave);

			log.info("Current progress: {} pages done, {} games done, {} games now have a power", pageable.getPageNumber(), totalGames, gamesWithPowerAdded);

			pageable = games.nextPageable();
		}

		logSender.sendLogs(log, "## Finished adding power to existing games in splatoon_3_vs_result table\n- total number of games: %d\n- games with power added: %d", totalGames, gamesWithPowerAdded);

		configurationRepository.save(Configuration.builder()
			.configName("Splatoon3PowerExporter_addMissingPowers_finished")
			.configValue("true")
			.build());
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of();
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("Splatoon3PowerExporter_addMissingPowers")
			.schedule(TickSchedule.getScheduleString(3))
			.runnable(this::addMissingPowers)
			.build());
	}
}

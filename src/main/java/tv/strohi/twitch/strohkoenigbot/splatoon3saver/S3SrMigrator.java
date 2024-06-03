package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrBossResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrBossResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3SrMigrator implements ScheduledService {
	private final LogSender logSender;
	private static final int PAGE_SIZE = 10;

	private final ConfigurationRepository configurationRepository;
	private final Splatoon3SrResultRepository srResultRepository;
	private final Splatoon3SrBossResultRepository srBossResultRepository;

	public void migrateSrGames() {
		var config = configurationRepository.findByConfigName("S3SrMigrator_success").isPresent();

		if (!config) {
			logSender.sendLogs(log, "Attempting to migrate all boss results into a dedicated table...");

			var count = 0;

			var bossResultsPageable = Pageable.ofSize(PAGE_SIZE);
			Page<Splatoon3SrResult> foundBossResults;
			while ((foundBossResults = srResultRepository.findAllByBossNotNull(bossResultsPageable)).hasContent()) {
				var realBossResults = foundBossResults
					.stream()
					.filter(result -> result.getEarnedBronzeScales() != null || result.getEarnedSilverScales() != null || result.getEarnedGoldScales() != null)
					.collect(Collectors.toList());

				for (var bossResult : realBossResults) {
					log.info("migrating {}", bossResult);

					try {
						srBossResultRepository.save(Splatoon3SrBossResult.builder()
							.result(bossResult)
							.boss(bossResult.getBoss())
							.defeated(bossResult.getSuccessful())
							.build());

						count++;
					} catch (Exception e) {
						log.error(e);
					}
				}

				if (foundBossResults.isLast()) {
					break;
				}

				bossResultsPageable = foundBossResults.nextPageable();
				log.info("sr boss results pageable now at {}", bossResultsPageable.getOffset());
			}

			configurationRepository.save(Configuration.builder()
				.configName("S3SrMigrator_success")
				.configValue("true")
				.build());

			logSender.sendLogs(log, String.format("Migrated %d boss results.", count));
		}
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of();
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3SrMigrator_schedule")
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::migrateSrGames)
			.build());
	}
}

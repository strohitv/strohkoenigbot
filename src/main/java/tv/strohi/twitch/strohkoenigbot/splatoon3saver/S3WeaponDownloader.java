package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.WeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class S3WeaponDownloader implements ScheduledService {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final List<Weapon> allWeapons = new ArrayList<>();

	public void setWeapons(List<Weapon> weapons) {
		allWeapons.clear();
		allWeapons.addAll(weapons);
	}

	public List<Weapon> getWeapons() {
		return List.copyOf(allWeapons);
	}

	private final AccountRepository accountRepository;

	private final S3ApiQuerySender requestSender;

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3WeaponDownloader_schedule")
			.schedule(CronSchedule.getScheduleString("45 1 * * * *"))
			.runnable(this::loadWeapons)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}

	public void loadWeapons() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			try {
				String allWeaponsResponse = requestSender.queryS3Api(account, S3RequestKey.Weapons);
				WeaponsResult ownedGear = new ObjectMapper().readValue(allWeaponsResponse, WeaponsResult.class);

				if (account.getIsMainAccount()) {
					setWeapons(Arrays.asList(ownedGear.getData().getWeaponRecords().getNodes()));
				}
			} catch (Exception e) {
				logSender.sendLogs(logger, "An exception occurred during S3 weapon download\nSee logs for details!");
				exceptionLogger.logException(logger, e);
			}
		}
	}
}

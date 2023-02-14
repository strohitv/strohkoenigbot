package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.WeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class S3WeaponDownloader {
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

	private final SchedulingService schedulingService;

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("S3WeaponDownloader_schedule", CronSchedule.getScheduleString("45 1 * * * *"), this::loadWeapons);
	}

	public void loadWeapons() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			try {
				String allWeaponsResponse = requestSender.queryS3Api(account, S3RequestKey.Weapons.getKey());
				WeaponsResult ownedGear = new ObjectMapper().readValue(allWeaponsResponse, WeaponsResult.class);

				if (account.getIsMainAccount()) {
					setWeapons(Arrays.asList(ownedGear.getData().getWeaponRecords().getNodes()));
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}

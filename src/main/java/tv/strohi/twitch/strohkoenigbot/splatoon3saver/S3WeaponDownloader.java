package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.Splatoon3VsResultService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.WeaponsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import javax.transaction.Transactional;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3WeaponDownloader implements ScheduledService {
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final Splatoon3VsWeaponRepository weaponRepository;

	private final Splatoon3VsResultService resultService;

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

	@Transactional
	public void loadWeapons() {
		List<Account> accounts = accountRepository.findByEnableSplatoon3(true);

		for (Account account : accounts) {
			try {
				if (!account.getIsMainAccount()) {
					continue;
				}

				var df = new DecimalFormat("#,###,###");

				var allWeaponsResponse = requestSender.queryS3Api(account, S3RequestKey.Weapons);
				var allWeapons = new ObjectMapper().readValue(allWeaponsResponse, WeaponsResult.class)
					.getData()
					.getWeaponRecords()
					.getNodes();

				setWeapons(Arrays.asList(allWeapons));

				var changes = new StringBuilder();
				for (var weapon : allWeapons) {
					var storedWeapon = resultService.ensureWeaponExists(weapon);

					var level = weapon.getStats().getLevel();
					var exp = getExp(weapon);
					var expRequired = weapon.getStats().getExpToLevelUp();
					if (storedWeapon.getWeaponLevel() != level
						|| storedWeapon.getExp() != exp
						|| storedWeapon.getExpRequired() != expRequired) {
						changes
							.append("\n- **")
							.append(storedWeapon.getName())
							.append("**: Level = `")
							.append(level)
							.append("` ");

						if (storedWeapon.getWeaponLevel() != level) {
							changes
								.append("(`")
								.append(level > storedWeapon.getWeaponLevel() ? "+ " : "- ")
								.append(Math.abs(level - storedWeapon.getWeaponLevel()))
								.append("`) ");
						}

						changes
							.append(", Exp = `")
							.append(df.format(exp).replace(",", " "))
							.append("` ");

						if (storedWeapon.getExp() != exp) {
							changes
								.append("(`")
								.append(exp > storedWeapon.getExp() ? "+ " : "- ")
								.append(df.format(Math.abs(exp - storedWeapon.getExp())).replace(",", " "))
								.append("`) ");
						}

						changes
							.append(", Exp required = `")
							.append(df.format(expRequired).replace(",", " "))
							.append("` ");

						if (storedWeapon.getExpRequired() != expRequired) {
							changes
								.append("(`")
								.append(expRequired > storedWeapon.getExpRequired() ? "+ " : "- ")
								.append(df.format(Math.abs(expRequired - storedWeapon.getExpRequired())).replace(",", " "))
								.append("`) ");
						}

						storedWeapon.setWeaponLevel(level);
						storedWeapon.setExp(exp);
						storedWeapon.setExpRequired(expRequired);

						weaponRepository.save(storedWeapon);
					}
				}

				if (changes.length() > 0) {
					logSender.queueLogs(
						log,
						"## Weapon stats have changed\nThe following weapon stats have changed in the last hour:\n%s",
						changes.toString().trim());
				}
			} catch (Exception e) {
				exceptionLogger.logExceptionAsAttachment(log, "An exception occurred during S3 weapon download\nSee logs for details!", e);
			}
		}
	}

	private int getExp(Weapon weapon) {
		var exp = 5_000;

		if (weapon.getStats().getLevel() >= 1) {
			exp += 20_000;
		}

		if (weapon.getStats().getLevel() >= 2) {
			exp += 35_000;
		}

		if (weapon.getStats().getLevel() >= 3) {
			exp += 100_000;
		}

		if (weapon.getStats().getLevel() >= 4) {
			exp += 1_000_000;
		}

		if (weapon.getStats().getLevel() >= 5) {
			exp += 840_000;
		}

		if (weapon.getStats().getLevel() >= 6) {
			exp += 1_000_000;
		}

		if (weapon.getStats().getLevel() >= 7) {
			exp += 1_000_000;
		}

		if (weapon.getStats().getLevel() >= 8) {
			exp += 1_000_000;
		}

		if (weapon.getStats().getLevel() >= 9) {
			exp += 1_000_000;
		}

		exp -= weapon.getStats().getExpToLevelUp();

		return exp;
	}
}

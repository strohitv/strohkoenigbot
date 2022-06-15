package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Stage;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2StageStats;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2StageStatsRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStatPage;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StatsExporter {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
	}

	private Splatoon2StageStatsRepository stageStatsRepository;

	@Autowired
	public void setStageStatsRepository(Splatoon2StageStatsRepository stageStatsRepository) {
		this.stageStatsRepository = stageStatsRepository;
	}

	private WeaponExporter weaponExporter;

	@Autowired
	public void setWeaponExporter(WeaponExporter weaponExporter) {
		this.weaponExporter = weaponExporter;
	}

	private StagesExporter stagesExporter;

	@Autowired
	public void setStagesExporter(StagesExporter stagesExporter) {
		this.stagesExporter = stagesExporter;
	}

	private RequestSender splatoonStatsLoader;

	@Autowired
	public void setSplatoonStatsLoader(RequestSender splatoonStatsLoader) {
		this.splatoonStatsLoader = splatoonStatsLoader;
	}

	@Scheduled(cron = "0 47 4 * * *")
	public void refreshStageAndWeaponStats() {
		logger.info("loading stage and weapon stats");

		Account account = accountRepository.findAll().stream()
				.filter(Account::getIsMainAccount)
				.findFirst()
				.orElse(new Account());

		SplatNetStatPage splatNetStatPage = splatoonStatsLoader.querySplatoonApiForAccount(account, "/api/records", SplatNetStatPage.class);

		logger.info("refreshing weapon stats");
		refreshWeaponStats(account.getId(), splatNetStatPage.getRecords().getWeapon_stats().values().stream()
				.sorted((w1, w2) -> -Integer.compare(w1.getWin_count(), w2.getWin_count()))
				.collect(Collectors.toList())
		);
		logger.info("refreshing stage stats");
		refreshStageStats(account.getId(), new ArrayList<>(splatNetStatPage.getRecords().getStage_stats().values()));
		logger.info("finished refresh");
	}

	private void refreshWeaponStats(long accountId, List<SplatNetStatPage.SplatNetRecords.SplatNetWeaponStats> loadedWeaponStats) {
		for (SplatNetStatPage.SplatNetRecords.SplatNetWeaponStats singleWeaponStats : loadedWeaponStats) {
			Splatoon2Weapon weapon = weaponExporter.loadWeapon(singleWeaponStats.getWeapon());
			Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(weapon.getId(), accountId).orElse(new Splatoon2WeaponStats(0L, weapon.getId(), accountId, 0L, 0, 0));

			boolean isDirty = weaponStats.getId() == 0L;

			if (!Objects.equals(weaponStats.getTurf(), singleWeaponStats.getTotal_paint_point())) {
				weaponStats.setTurf(singleWeaponStats.getTotal_paint_point());
				isDirty = true;
			}

			if (!Objects.equals(weaponStats.getWins(), singleWeaponStats.getWin_count())) {
				weaponStats.setWins(singleWeaponStats.getWin_count());
				isDirty = true;
			}

			if (!Objects.equals(weaponStats.getDefeats(), singleWeaponStats.getLose_count())) {
				weaponStats.setDefeats(singleWeaponStats.getLose_count());
				isDirty = true;
			}

			if (isDirty) {
				weaponStatsRepository.save(weaponStats);
			}
		}
	}

	private void refreshStageStats(long accountId, List<SplatNetStatPage.SplatNetRecords.SplatNetStageStats> loadedStageStats) {
		for (SplatNetStatPage.SplatNetRecords.SplatNetStageStats singleStageStats : loadedStageStats) {
			Splatoon2Stage stage = stagesExporter.loadStage(singleStageStats.getStage());
			Splatoon2StageStats stageStats = stageStatsRepository.findByStageIdAndAccountId(stage.getId(), accountId)
					.orElse(new Splatoon2StageStats(0L, stage.getId(), accountId, 0, 0, 0, 0, 0, 0, 0, 0));

			boolean isDirty = stageStats.getId() == 0L;

			if (!Objects.equals(stageStats.getZonesWins(), singleStageStats.getZonesWinCount())) {
				stageStats.setZonesWins(singleStageStats.getZonesWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getZonesDefeats(), singleStageStats.getZonesLoseCount())) {
				stageStats.setZonesDefeats(singleStageStats.getZonesLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getRainmakerWins(), singleStageStats.getRainmakerWinCount())) {
				stageStats.setRainmakerWins(singleStageStats.getRainmakerWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getRainmakerDefeats(), singleStageStats.getRainmakerLoseCount())) {
				stageStats.setRainmakerDefeats(singleStageStats.getRainmakerLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getTowerWins(), singleStageStats.getTowerWinCount())) {
				stageStats.setTowerWins(singleStageStats.getTowerWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getTowerDefeats(), singleStageStats.getTowerLoseCount())) {
				stageStats.setTowerDefeats(singleStageStats.getTowerLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getClamsWins(), singleStageStats.getClamsWinCount())) {
				stageStats.setClamsWins(singleStageStats.getClamsWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stageStats.getClamsDefeats(), singleStageStats.getClamsLoseCount())) {
				stageStats.setClamsDefeats(singleStageStats.getClamsLoseCount());
				isDirty = true;
			}

			if (isDirty) {
				stageStatsRepository.save(stageStats);
			}
		}
	}
}

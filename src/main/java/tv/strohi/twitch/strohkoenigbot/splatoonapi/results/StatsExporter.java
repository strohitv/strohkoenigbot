package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonStage;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonStageRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetStatPage;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StatsExporter {
	private SplatoonWeaponRepository weaponRepository;

	@Autowired
	public void setWeaponRepository(SplatoonWeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	private SplatoonStageRepository stageRepository;

	@Autowired
	public void setStageRepository(SplatoonStageRepository stageRepository) {
		this.stageRepository = stageRepository;
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
		SplatNetStatPage splatNetStatPage = splatoonStatsLoader.querySplatoonApi("/api/records", SplatNetStatPage.class);

		refreshWeaponStats(splatNetStatPage.getRecords().getWeapon_stats().values().stream()
				.sorted((w1, w2) -> -Integer.compare(w1.getWin_count(), w2.getWin_count()))
				.collect(Collectors.toList())
		);

		refreshStageStats(new ArrayList<>(splatNetStatPage.getRecords().getStage_stats().values()));
	}

	private void refreshWeaponStats(List<SplatNetStatPage.SplatNetRecords.SplatNetWeaponStats> weaponStats) {
		for (SplatNetStatPage.SplatNetRecords.SplatNetWeaponStats singleWeaponStats : weaponStats) {
			SplatoonWeapon weapon = weaponExporter.loadWeapon(singleWeaponStats.getWeapon());

			boolean isDirty = false;

			if (!Objects.equals(weapon.getTurf(), singleWeaponStats.getTotal_paint_point())) {
				weapon.setTurf(singleWeaponStats.getTotal_paint_point());
				isDirty = true;
			}

			if (!Objects.equals(weapon.getWins(), singleWeaponStats.getWin_count())) {
				weapon.setWins(singleWeaponStats.getWin_count());
				isDirty = true;
			}

			if (!Objects.equals(weapon.getDefeats(), singleWeaponStats.getLose_count())) {
				weapon.setDefeats(singleWeaponStats.getLose_count());
				isDirty = true;
			}

			if (isDirty) {
				weaponRepository.save(weapon);
			}
		}
	}

	private void refreshStageStats(List<SplatNetStatPage.SplatNetRecords.SplatNetStageStats> stageStats) {
		for (SplatNetStatPage.SplatNetRecords.SplatNetStageStats singleStageStats : stageStats) {
			SplatoonStage stage = stagesExporter.loadStage(singleStageStats.getStage());

			boolean isDirty = false;

			if (!Objects.equals(stage.getZonesWins(), singleStageStats.getZonesWinCount())) {
				stage.setZonesWins(singleStageStats.getZonesWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getZonesDefeats(), singleStageStats.getZonesLoseCount())) {
				stage.setZonesDefeats(singleStageStats.getZonesLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getRainmakerWins(), singleStageStats.getRainmakerWinCount())) {
				stage.setRainmakerWins(singleStageStats.getRainmakerWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getRainmakerDefeats(), singleStageStats.getRainmakerLoseCount())) {
				stage.setRainmakerDefeats(singleStageStats.getRainmakerLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getTowerWins(), singleStageStats.getTowerWinCount())) {
				stage.setTowerWins(singleStageStats.getTowerWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getTowerDefeats(), singleStageStats.getTowerLoseCount())) {
				stage.setTowerDefeats(singleStageStats.getTowerLoseCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getClamsWins(), singleStageStats.getClamsWinCount())) {
				stage.setClamsWins(singleStageStats.getClamsWinCount());
				isDirty = true;
			}

			if (!Objects.equals(stage.getClamsDefeats(), singleStageStats.getClamsLoseCount())) {
				stage.setClamsDefeats(singleStageStats.getClamsLoseCount());
				isDirty = true;
			}

			if (isDirty) {
				stageRepository.save(stage);
			}
		}
	}
}

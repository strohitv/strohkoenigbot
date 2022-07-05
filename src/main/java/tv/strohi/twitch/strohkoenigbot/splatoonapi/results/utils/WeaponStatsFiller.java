package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;

@Component
public class WeaponStatsFiller {
	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
	}

	public Splatoon2WeaponStats fill(Splatoon2Match match, SplatNetMatchResult singleResult, long weaponId, long accountId) {
		Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(weaponId, accountId).orElseGet(() -> {
			Splatoon2WeaponStats ws = new Splatoon2WeaponStats();
			ws.setWeaponId(weaponId);
			ws.setAccountId(accountId);
			ws.setTurf(0L);
			ws.setWins(0);
			ws.setDefeats(0);
			ws.setCurrentFlag(0.0);
			ws.setMaxFlag(0.0);
			return ws;
		});

		if (singleResult.getWin_meter() != null) {
			weaponStats.setCurrentFlag(singleResult.getWin_meter());

			if (weaponStats.getMaxFlag() == null || weaponStats.getMaxFlag() < singleResult.getWin_meter()) {
				weaponStats.setMaxFlag(singleResult.getWin_meter());
			}
		}

		weaponStats.setTurf(singleResult.getWeapon_paint_point());
		if (match.getMatchResult() == Splatoon2MatchResult.Win) {
			weaponStats.setWins(weaponStats.getWins() + 1);
		} else {
			weaponStats.setDefeats(weaponStats.getDefeats() + 1);
		}

		weaponStats = weaponStatsRepository.save(weaponStats);

		return weaponStats;
	}
}

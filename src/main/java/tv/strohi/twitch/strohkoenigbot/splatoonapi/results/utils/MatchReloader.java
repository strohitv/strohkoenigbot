package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2AbilityMatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2ClipRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2MatchRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponStatsRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;

import java.util.List;

@Component
public class MatchReloader {
	private Splatoon2MatchRepository matchRepository;

	@Autowired
	public void setMatchRepository(Splatoon2MatchRepository matchRepository) {
		this.matchRepository = matchRepository;
	}

	private Splatoon2AbilityMatchRepository abilityMatchRepository;

	@Autowired
	public void setAbilityMatchRepository(Splatoon2AbilityMatchRepository abilityMatchRepository) {
		this.abilityMatchRepository = abilityMatchRepository;
	}

	private Splatoon2ClipRepository clipRepository;

	@Autowired
	public void setClipRepository(Splatoon2ClipRepository clipRepository) {
		this.clipRepository = clipRepository;
	}

	private Splatoon2WeaponStatsRepository weaponStatsRepository;

	@Autowired
	public void setWeaponStatsRepository(Splatoon2WeaponStatsRepository weaponStatsRepository) {
		this.weaponStatsRepository = weaponStatsRepository;
	}

	public void forceMatchReload(Account account, List<SplatNetMatchResult> results) {
		for (SplatNetMatchResult singleResult : results) {
			Splatoon2Match match = matchRepository.findByAccountIdAndBattleNumber(account.getId(), singleResult.getBattle_number());

			if (match != null) {
				long id = match.getId();

				clipRepository.getAllByMatchId(id).forEach(clip -> {
					clip.setMatchId(null);
					clipRepository.save(clip);
				});

				Splatoon2WeaponStats weaponStats = weaponStatsRepository.findByWeaponIdAndAccountId(match.getWeaponId(), account.getId()).orElse(null);
				if (weaponStats != null) {
					if (match.getMatchResult() == Splatoon2MatchResult.Win) {
						weaponStats.setWins(weaponStats.getWins() - 1);
					} else {
						weaponStats.setDefeats(weaponStats.getDefeats() - 1);
					}

					weaponStatsRepository.save(weaponStats);
				}

				abilityMatchRepository.deleteAll(abilityMatchRepository.findAllByMatchId(id));
				matchRepository.delete(match);
			}
		}
	}
}

package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonWeaponRequestRanking;

import java.util.List;

@Repository
public interface SplatoonWeaponRequestRankingRepository extends CrudRepository<SplatoonWeaponRequestRanking, Long> {
	@NotNull List<SplatoonWeaponRequestRanking> findAllByOrderByWinStreakDescChallengedAtAsc();
	@NotNull List<SplatoonWeaponRequestRanking> findAllByTwitchIdOrderByWinStreakDescChallengedAtAsc(String twitchId);
}

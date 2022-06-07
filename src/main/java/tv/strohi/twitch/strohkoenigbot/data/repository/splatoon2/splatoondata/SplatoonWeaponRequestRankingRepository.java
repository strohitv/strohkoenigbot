package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponRequestRanking;

import java.util.List;

@Repository
public interface SplatoonWeaponRequestRankingRepository extends CrudRepository<Splatoon2WeaponRequestRanking, Long> {
	@NotNull List<Splatoon2WeaponRequestRanking> findAllByOrderByWinStreakDescChallengedAtAsc();
	@NotNull List<Splatoon2WeaponRequestRanking> findAllByTwitchIdOrderByWinStreakDescChallengedAtAsc(String twitchId);
}

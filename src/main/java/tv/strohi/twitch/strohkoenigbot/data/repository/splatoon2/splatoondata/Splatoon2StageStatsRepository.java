package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2StageStats;

import java.util.Optional;

@Repository
public interface Splatoon2StageStatsRepository extends CrudRepository<Splatoon2StageStats, Long> {
	Splatoon2StageStats findById(long id);
	Optional<Splatoon2StageStats> findByStageIdAndAccountId(long stageId, long accountId);
}

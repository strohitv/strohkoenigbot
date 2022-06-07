package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonStage;

@Repository
public interface SplatoonStageRepository extends CrudRepository<SplatoonStage, Long> {
	SplatoonStage findById(long id);

	SplatoonStage findBySplatoonApiId(String splatoonApiId);
}

package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonStage;

@Repository
public interface SplatoonStageRepository extends CrudRepository<SplatoonStage, Long> {
	SplatoonStage findById(long id);

	SplatoonStage findBySplatoonApiId(String splatoonApiId);
}

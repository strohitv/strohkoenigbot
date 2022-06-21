package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Stage;

@Repository
public interface Splatoon2StageRepository extends CrudRepository<Splatoon2Stage, Long> {
	Splatoon2Stage findById(long id);

	Splatoon2Stage findBySplatoonApiId(String splatoonApiId);
}

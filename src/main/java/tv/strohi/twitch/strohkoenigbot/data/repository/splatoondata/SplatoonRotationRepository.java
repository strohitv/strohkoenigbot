package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonRotation;

@Repository
public interface SplatoonRotationRepository extends CrudRepository<SplatoonRotation, Long> {
	SplatoonRotation findBySplatoonApiId(Long splatoonApiId);
}

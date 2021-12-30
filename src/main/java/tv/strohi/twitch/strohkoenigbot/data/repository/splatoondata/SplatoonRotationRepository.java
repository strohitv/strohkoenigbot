package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonRotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;

@Repository
public interface SplatoonRotationRepository extends CrudRepository<SplatoonRotation, Long> {
	SplatoonRotation findBySplatoonApiIdAndMode(Long splatoonApiId, SplatoonMode mode);
}

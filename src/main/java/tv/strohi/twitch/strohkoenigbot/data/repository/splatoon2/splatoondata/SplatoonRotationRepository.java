package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonRotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonMode;

@Repository
public interface SplatoonRotationRepository extends CrudRepository<SplatoonRotation, Long> {
	SplatoonRotation findBySplatoonApiIdAndMode(Long splatoonApiId, SplatoonMode mode);

	SplatoonRotation findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(long startTime, long endTime, SplatoonMode mode);
}

package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Rotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;

@Repository
public interface Splatoon2RotationRepository extends CrudRepository<Splatoon2Rotation, Long> {
	Splatoon2Rotation findBySplatoonApiIdAndMode(Long splatoonApiId, Splatoon2Mode mode);

	Splatoon2Rotation findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(long startTime, long endTime, Splatoon2Mode mode);
}

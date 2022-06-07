package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Clip;

import java.util.List;

@Repository
public interface Splatoon2ClipRepository extends CrudRepository<Splatoon2Clip, Long> {
	List<Splatoon2Clip> getAllByStartTimeIsGreaterThanAndEndTimeIsLessThan(long startTime, long endTime);
	List<Splatoon2Clip> getAllByMatchId(long matchId);
}

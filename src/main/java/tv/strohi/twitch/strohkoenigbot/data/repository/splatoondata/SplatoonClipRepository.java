package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonClip;

import java.util.List;

@Repository
public interface SplatoonClipRepository extends CrudRepository<SplatoonClip, Long> {
	List<SplatoonClip> getAllByStartTimeIsGreaterThanAndEndTimeIsLessThan(long startTime, long endTime);
	List<SplatoonClip> getAllByMatchId(long matchId);
}

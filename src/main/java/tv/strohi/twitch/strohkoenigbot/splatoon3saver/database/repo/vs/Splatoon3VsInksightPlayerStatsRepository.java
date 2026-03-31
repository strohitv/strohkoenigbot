package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsInksightPlayerStats;

@Repository
public interface Splatoon3VsInksightPlayerStatsRepository extends CrudRepository<Splatoon3VsInksightPlayerStats, Long> {
}

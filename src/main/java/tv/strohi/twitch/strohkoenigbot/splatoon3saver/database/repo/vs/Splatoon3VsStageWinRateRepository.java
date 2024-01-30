package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsStageWinRate;

import java.util.Optional;

@Repository
public interface Splatoon3VsStageWinRateRepository extends CrudRepository<Splatoon3VsStageWinRate, Long> {
	Optional<Splatoon3VsStageWinRate> findById(long id);
}

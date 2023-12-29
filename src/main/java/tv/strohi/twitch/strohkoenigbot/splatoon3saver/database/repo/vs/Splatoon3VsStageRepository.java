package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsStage;

import java.util.Optional;

@Repository
public interface Splatoon3VsStageRepository extends CrudRepository<Splatoon3VsStage, Long> {
	Optional<Splatoon3VsStage> findById(long id);
	Optional<Splatoon3VsStage> findByApiId(String id);
}

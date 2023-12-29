package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrStage;

import java.util.Optional;

@Repository
public interface Splatoon3SrStageRepository extends CrudRepository<Splatoon3SrStage, Long> {
	Optional<Splatoon3SrStage> findById(long id);
	Optional<Splatoon3SrStage> findByApiId(String id);
}

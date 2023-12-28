package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;

import java.util.Optional;

@Repository
public interface Splatoon3VsRotationRepository extends CrudRepository<Splatoon3VsRotation, Long> {
	Optional<Splatoon3VsRotation> findById(long id);
}

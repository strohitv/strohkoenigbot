package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;

import java.util.Optional;

@Repository
public interface Splatoon3SrRotationRepository extends CrudRepository<Splatoon3SrRotation, Long> {
	Optional<Splatoon3SrRotation> findById(long id);
}

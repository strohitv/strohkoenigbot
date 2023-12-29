package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface Splatoon3VsRotationRepository extends CrudRepository<Splatoon3VsRotation, Long> {
	Optional<Splatoon3VsRotation> findById(long id);

	Optional<Splatoon3VsRotation> findByModeAndStartTime(Splatoon3VsMode mode, Instant startTime);
}

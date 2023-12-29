package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrRotation;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface Splatoon3SrRotationRepository extends CrudRepository<Splatoon3SrRotation, Long> {
	Optional<Splatoon3SrRotation> findById(long id);
	Optional<Splatoon3SrRotation> findByModeAndStartTime(Splatoon3SrMode mode, Instant startTime);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsRotationRepository extends CrudRepository<Splatoon3VsRotation, Long> {
	Optional<Splatoon3VsRotation> findById(long id);

	Optional<Splatoon3VsRotation> findByModeAndStartTime(Splatoon3VsMode mode, Instant startTime);
	List<Splatoon3VsRotation> findByModeAndStartTimeAfter(Splatoon3VsMode mode, Instant startTimeAfter);

	List<Splatoon3VsRotation> findByStartTimeAfterAndStartTimeBefore(Instant startTimeAfter, Instant startTimeBefore);
}

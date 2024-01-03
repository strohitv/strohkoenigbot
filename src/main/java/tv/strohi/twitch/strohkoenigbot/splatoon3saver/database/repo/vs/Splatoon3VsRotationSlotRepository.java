package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationSlot;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface Splatoon3VsRotationSlotRepository extends CrudRepository<Splatoon3VsRotationSlot, Long> {
	Optional<Splatoon3VsRotationSlot> findById(long id);
	Optional<Splatoon3VsRotationSlot> findByStartTime(Instant startTime);
}

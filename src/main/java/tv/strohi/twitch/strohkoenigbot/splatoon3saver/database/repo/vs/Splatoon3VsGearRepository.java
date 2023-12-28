package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGear;

import java.util.Optional;

@Repository
public interface Splatoon3VsGearRepository extends CrudRepository<Splatoon3VsGear, Long> {
	Optional<Splatoon3VsGear> findById(long id);
}

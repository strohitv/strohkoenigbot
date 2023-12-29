package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsEventRegulation;

import java.util.Optional;

@Repository
public interface Splatoon3VsEventRegulationRepository extends CrudRepository<Splatoon3VsEventRegulation, Long> {
	Optional<Splatoon3VsEventRegulation> findById(long id);
	Optional<Splatoon3VsEventRegulation> findByApiId(String apiId);
}

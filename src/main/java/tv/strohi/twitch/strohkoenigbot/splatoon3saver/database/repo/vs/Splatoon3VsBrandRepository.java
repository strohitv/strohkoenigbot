package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsBrand;

import java.util.Optional;

@Repository
public interface Splatoon3VsBrandRepository extends CrudRepository<Splatoon3VsBrand, Long> {
	Optional<Splatoon3VsBrand> findById(long id);
}

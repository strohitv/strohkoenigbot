package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrMode;

import java.util.Optional;

@Repository
public interface Splatoon3SrModeRepository extends CrudRepository<Splatoon3SrMode, Long> {
	Optional<Splatoon3SrMode> findById(long id);
	Optional<Splatoon3SrMode> findByApiTypename(String apiTypename);
	Optional<Splatoon3SrMode> findByApiModeAndApiRule(String apiMode, String apiRule);
	Optional<Splatoon3SrMode> findByApiRule(String apiRule);
}

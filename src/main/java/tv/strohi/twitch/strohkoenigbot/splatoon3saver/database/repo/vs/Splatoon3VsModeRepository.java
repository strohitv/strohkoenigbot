package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;

import java.util.Optional;

@Repository
public interface Splatoon3VsModeRepository extends CrudRepository<Splatoon3VsMode, Long> {
	Optional<Splatoon3VsMode> findById(long id);
	Optional<Splatoon3VsMode> findByApiId(String apiId);
	Optional<Splatoon3VsMode> findByApiTypenameAndApiModeDistinction(String apiTypename, String apiModeDistinction);
}

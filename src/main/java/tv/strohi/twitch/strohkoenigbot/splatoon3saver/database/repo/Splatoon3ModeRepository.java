package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3Mode;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3ModeRepository extends CrudRepository<Splatoon3Mode, Long> {
	Optional<Splatoon3Mode> findById(long id);

	List<Splatoon3Mode> findAllByApiTypename(String apiTypename);

	Optional<Splatoon3Mode> findByApiTypenameAndApiBankaraMode(String apiTypename, String apiBankaraMode);
}

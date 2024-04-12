package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3RequestKey;

import java.util.Optional;

@Repository
public interface Splatoon3RequestKeyRepository extends CrudRepository<Splatoon3RequestKey, Long> {
	Optional<Splatoon3RequestKey> findByQueryName(String queryName);
	Optional<Splatoon3RequestKey> findByQueryHash(String queryHash);
	Optional<Splatoon3RequestKey> findByQueryPath(String queryPath);
}

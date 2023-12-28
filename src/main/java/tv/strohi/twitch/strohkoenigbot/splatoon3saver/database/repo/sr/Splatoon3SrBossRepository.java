package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrBoss;

import java.util.Optional;

@Repository
public interface Splatoon3SrBossRepository extends CrudRepository<Splatoon3SrBoss, Long> {
	Optional<Splatoon3SrBoss> findById(long id);
}

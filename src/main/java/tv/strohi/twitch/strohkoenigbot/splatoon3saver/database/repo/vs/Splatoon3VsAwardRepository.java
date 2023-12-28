package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsAward;

import java.util.Optional;

@Repository
public interface Splatoon3VsAwardRepository extends CrudRepository<Splatoon3VsAward, Long> {
	Optional<Splatoon3VsAward> findById(long id);
}

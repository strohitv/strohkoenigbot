package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultPlayer;

import java.util.Optional;

@Repository
public interface Splatoon3SrResultPlayerRepository extends CrudRepository<Splatoon3SrResultPlayer, Long> {
	Optional<Splatoon3SrResultPlayer> findById(long id);
}

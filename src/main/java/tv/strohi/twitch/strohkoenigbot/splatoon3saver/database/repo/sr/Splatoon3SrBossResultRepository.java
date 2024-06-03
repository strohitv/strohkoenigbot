package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrBossResult;

import java.util.Optional;

@Repository
public interface Splatoon3SrBossResultRepository extends CrudRepository<Splatoon3SrBossResult, Long> {
	Optional<Splatoon3SrBossResult> findById(long id);
}

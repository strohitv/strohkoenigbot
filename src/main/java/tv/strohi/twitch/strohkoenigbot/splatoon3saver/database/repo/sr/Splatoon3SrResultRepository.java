package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResult;

import java.util.Optional;

@Repository
public interface Splatoon3SrResultRepository extends CrudRepository<Splatoon3SrResult, Long> {
	Optional<Splatoon3SrResult> findById(long id);
	Optional<Splatoon3SrResult> findByApiId(String apiId);
	Optional<Splatoon3SrResult> findTop1ByOrderByPlayedTimeDesc();
}

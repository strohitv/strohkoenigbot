package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrEventWave;

import java.util.Optional;

@Repository
public interface Splatoon3SrEventWaveRepository extends CrudRepository<Splatoon3SrEventWave, Long> {
	Optional<Splatoon3SrEventWave> findByApiId(String apiId);
}

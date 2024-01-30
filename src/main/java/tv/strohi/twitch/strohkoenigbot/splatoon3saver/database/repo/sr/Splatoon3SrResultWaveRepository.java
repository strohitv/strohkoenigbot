package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultWave;

import java.util.Optional;

@Repository
public interface Splatoon3SrResultWaveRepository extends CrudRepository<Splatoon3SrResultWave, Long> {
	Optional<Splatoon3SrResultWave> findByResultIdAndWaveNumber(long resultId, int waveNumber);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultEnemy;

import java.util.Optional;

@Repository
public interface Splatoon3SrResultEnemyRepository extends CrudRepository<Splatoon3SrResultEnemy, Long> {
	Optional<Splatoon3SrResultEnemy> findByResultIdAndEnemyId(long resultId, long enemyId);
}

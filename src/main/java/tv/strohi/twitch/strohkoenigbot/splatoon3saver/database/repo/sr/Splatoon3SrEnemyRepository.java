package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrEnemy;

import java.util.Optional;

@Repository
public interface Splatoon3SrEnemyRepository extends CrudRepository<Splatoon3SrEnemy, Long> {
	Optional<Splatoon3SrEnemy> findByApiId(String apiId);
}

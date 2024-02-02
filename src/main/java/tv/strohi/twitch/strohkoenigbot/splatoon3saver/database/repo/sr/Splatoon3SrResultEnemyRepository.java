package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultEnemy;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.model.EnemyNameOwnDestroyCount;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3SrResultEnemyRepository extends CrudRepository<Splatoon3SrResultEnemy, Long> {
	Optional<Splatoon3SrResultEnemy> findByResultIdAndEnemyId(long resultId, long enemyId);

	Page<Splatoon3SrResultEnemy> findAll(Pageable pageable);

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.model.EnemyNameOwnDestroyCount(e.name, SUM(re.ownDestroyCount))" +
		" FROM splatoon_3_sr_result_enemy re" +
		" JOIN splatoon_3_sr_enemy e ON e.id = re.enemyId" +
		" GROUP BY e.name")
	List<EnemyNameOwnDestroyCount> findOwnDestroySumGroupByEnemyId();
}

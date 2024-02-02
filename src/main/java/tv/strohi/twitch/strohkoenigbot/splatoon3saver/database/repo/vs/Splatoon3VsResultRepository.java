package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;

import java.util.Optional;

@Repository
public interface Splatoon3VsResultRepository extends CrudRepository<Splatoon3VsResult, Long> {
	Optional<Splatoon3VsResult> findById(long id);
	Optional<Splatoon3VsResult> findByApiId(String apiId);
	Optional<Splatoon3VsResult> findTop1ByOrderByPlayedTimeDesc();

	Page<Splatoon3VsResult> findAll(Pageable pageable);
}

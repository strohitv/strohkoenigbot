package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.BuildFilter;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildFilterRepository extends CrudRepository<BuildFilter, Long> {
	Optional<BuildFilter> findById(long id);

	Optional<BuildFilter> findByName(String imageUrl);

	List<BuildFilter> findAllByNameIn(List<String> names);
}

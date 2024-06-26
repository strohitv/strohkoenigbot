package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAccess;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwitchAccessRepository extends CrudRepository<TwitchAccess, Long> {
	@NotNull List<TwitchAccess> findAll();

	Optional<TwitchAccess> findById(long id);
	Optional<TwitchAccess> findByUserId(String id);
}

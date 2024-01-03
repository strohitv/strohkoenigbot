package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Badge;

import java.util.Optional;

@Repository
public interface Splatoon3BadgeRepository extends CrudRepository<Splatoon3Badge, Long> {
	Optional<Splatoon3Badge> findById(long id);

	Optional<Splatoon3Badge> findByApiId(String apiId);
}

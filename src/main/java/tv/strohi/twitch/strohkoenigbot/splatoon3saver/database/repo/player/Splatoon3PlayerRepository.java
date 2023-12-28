package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3PlayerRepository extends CrudRepository<Splatoon3Player, Long> {
	Optional<Splatoon3Player> findById(long id);

	List<Splatoon3Player> findAllByApiId(String apiId);

	List<Splatoon3Player> findAllByApiPrefixedId(String apiPrefixedId);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsTeamPlayer;

import java.util.Optional;

@Repository
public interface Splatoon3VsTeamPlayerRepository extends CrudRepository<Splatoon3VsTeamPlayer, Long> {
	Optional<Splatoon3VsTeamPlayer> findByTeamIdAndPlayerId(long teamId, long playerId);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;

import java.util.Optional;

@Repository
public interface Splatoon3VsResultTeamPlayerRepository extends CrudRepository<Splatoon3VsResultTeamPlayer, Long> {
	Optional<Splatoon3VsResultTeamPlayer> findByResultIdAndTeamOrderAndPlayerId(long resultId, int teamOrder, long playerId);
}

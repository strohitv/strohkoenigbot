package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsTeam;

import java.util.Optional;

@Repository
public interface Splatoon3VsTeamRepository extends CrudRepository<Splatoon3VsTeam, Long> {
	Optional<Splatoon3VsTeam> findById(long id);
}

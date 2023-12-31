package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;

import java.util.Optional;

@Repository
public interface Splatoon3VsResultTeamRepository extends CrudRepository<Splatoon3VsResultTeam, Long> {
	Optional<Splatoon3VsResultTeam> findByResultIdAndTeamOrder(long result, int teamOrder);
}

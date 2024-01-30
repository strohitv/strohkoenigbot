package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsAbility;

import java.util.Optional;

@Repository
public interface Splatoon3VsAbilityRepository extends CrudRepository<Splatoon3VsAbility, Long> {
	Optional<Splatoon3VsAbility> findByName(String name);
}

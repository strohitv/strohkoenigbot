package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSubWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3VsSubWeaponRepository extends CrudRepository<Splatoon3VsSubWeapon, Long> {
	Optional<Splatoon3VsSubWeapon> findByApiId(String apiId);
}

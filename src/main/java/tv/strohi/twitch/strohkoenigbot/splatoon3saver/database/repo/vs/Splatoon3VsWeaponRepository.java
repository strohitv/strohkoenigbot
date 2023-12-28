package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3VsWeaponRepository extends CrudRepository<Splatoon3VsWeapon, Long> {
	Optional<Splatoon3VsWeapon> findById(long id);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3SrWeaponRepository extends CrudRepository<Splatoon3SrWeapon, Long> {
	Optional<Splatoon3SrWeapon> findById(long id);
}

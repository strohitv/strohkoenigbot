package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrSpecialWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3SrSpecialWeaponRepository extends CrudRepository<Splatoon3SrSpecialWeapon, Long> {
	Optional<Splatoon3SrSpecialWeapon> findByName(String name);
}

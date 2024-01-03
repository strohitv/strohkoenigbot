package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrWaveUsedSpecialWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3SrWaveUsedSpecialWeaponRepository extends CrudRepository<Splatoon3SrWaveUsedSpecialWeapon, Long> {
	Optional<Splatoon3SrWaveUsedSpecialWeapon> findById(long id);
}

package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultWave;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultWaveUsedSpecialWeapon;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3SrResultWaveUsedSpecialWeaponRepository extends CrudRepository<Splatoon3SrResultWaveUsedSpecialWeapon, Long> {
	Optional<Splatoon3SrResultWaveUsedSpecialWeapon> findById(long id);
	List<Splatoon3SrResultWaveUsedSpecialWeapon> findAllByResultWave(Splatoon3SrResultWave resultWave);
}

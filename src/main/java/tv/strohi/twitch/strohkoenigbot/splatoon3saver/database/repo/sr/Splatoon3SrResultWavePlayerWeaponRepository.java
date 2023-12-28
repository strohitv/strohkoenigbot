package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrResultWavePlayerWeapon;

import java.util.Optional;

@Repository
public interface Splatoon3SrResultWavePlayerWeaponRepository extends CrudRepository<Splatoon3SrResultWavePlayerWeapon, Long> {
	Optional<Splatoon3SrResultWavePlayerWeapon> findByResultIdAndWaveNumberAndPlayerIdAndWeaponId(long resultId, int waveNumber, long playerId, long weaponId);
}

package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;

import java.util.List;

@Repository
public interface SplatoonWeaponRepository extends CrudRepository<SplatoonWeapon, Long> {
	SplatoonWeapon findBySplatoonApiId(String splatoonApiId);

	SplatoonWeapon findByName(String name);

	List<SplatoonWeapon> findByTurf(long turf);
	List<SplatoonWeapon> findByTurfGreaterThan(long turf);
	List<SplatoonWeapon> findByTurfGreaterThanEqual(long turf);
	List<SplatoonWeapon> findByTurfLessThan(long turf);
	List<SplatoonWeapon> findByTurfLessThanEqual(long turf);
}

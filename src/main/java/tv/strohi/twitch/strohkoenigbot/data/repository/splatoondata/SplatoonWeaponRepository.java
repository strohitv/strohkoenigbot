package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;

import java.util.List;

@Repository
public interface SplatoonWeaponRepository extends CrudRepository<SplatoonWeapon, Long> {
	SplatoonWeapon findBySplatoonApiId(String splatoonApiId);
	List<SplatoonWeapon> findByTurfGreaterThanEqual(long turf);
}

package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonWeapon;

import java.util.List;

@Repository
public interface SplatoonWeaponRepository extends CrudRepository<SplatoonWeapon, Long> {
	SplatoonWeapon findBySplatoonApiId(String splatoonApiId);

	SplatoonWeapon findByName(String name);

	@NotNull List<SplatoonWeapon> findAll();
	@NotNull List<SplatoonWeapon> findByTurf(long turf);
	@NotNull List<SplatoonWeapon> findByTurfGreaterThan(long turf);
	@NotNull List<SplatoonWeapon> findByTurfGreaterThanEqual(long turf);
	@NotNull List<SplatoonWeapon> findByTurfLessThan(long turf);
	@NotNull List<SplatoonWeapon> findByTurfLessThanEqual(long turf);
}

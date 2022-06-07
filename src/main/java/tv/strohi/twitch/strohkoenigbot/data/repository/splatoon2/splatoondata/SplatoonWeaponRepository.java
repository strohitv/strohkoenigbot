package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;

import java.util.List;

@Repository
public interface SplatoonWeaponRepository extends CrudRepository<Splatoon2Weapon, Long> {
	Splatoon2Weapon findBySplatoonApiId(String splatoonApiId);

	Splatoon2Weapon findByName(String name);

	@NotNull List<Splatoon2Weapon> findAll();
	@NotNull List<Splatoon2Weapon> findByTurf(long turf);
	@NotNull List<Splatoon2Weapon> findByTurfGreaterThan(long turf);
	@NotNull List<Splatoon2Weapon> findByTurfGreaterThanEqual(long turf);
	@NotNull List<Splatoon2Weapon> findByTurfLessThan(long turf);
	@NotNull List<Splatoon2Weapon> findByTurfLessThanEqual(long turf);
}

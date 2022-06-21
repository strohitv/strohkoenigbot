package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponStats;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon2WeaponStatsRepository extends CrudRepository<Splatoon2WeaponStats, Long> {
	Optional<Splatoon2WeaponStats> findByWeaponIdAndAccountId(long weaponId, long accountId);
	@NotNull List<Splatoon2WeaponStats> findAll();
	@NotNull List<Splatoon2WeaponStats> findByTurfAndAccountId(long turf, long accountId);
	@NotNull List<Splatoon2WeaponStats> findByTurfGreaterThanAndAccountId(long turf, long accountId);
	@NotNull List<Splatoon2WeaponStats> findByTurfGreaterThanEqualAndAccountId(long turf, long accountId);
	@NotNull List<Splatoon2WeaponStats> findByTurfLessThanAndAccountId(long turf, long accountId);
	@NotNull List<Splatoon2WeaponStats> findByAccountId(long accountId);
	@NotNull List<Splatoon2WeaponStats> findByTurfLessThanEqualAndAccountId(long turf, long accountId);
}

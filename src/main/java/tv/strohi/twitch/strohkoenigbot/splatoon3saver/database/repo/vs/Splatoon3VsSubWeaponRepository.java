package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import feign.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSubWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsSubWeaponRepository extends CrudRepository<Splatoon3VsSubWeapon, Long> {
	Optional<Splatoon3VsSubWeapon> findByApiId(String apiId);

	@Query("SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon('TOTAL', subWeapon.id as subWeaponId, COUNT(*) as total, COUNT(case when result.ownJudgement like 'WIN' then 1 end) as wins, COUNT(case when result.ownJudgement like 'LOSE' then 1 end) as defeats) " +
		"FROM splatoon_3_vs_result_team_player rtp " +
		"JOIN splatoon_3_vs_weapon weapon on weapon.id = rtp.weapon.id " +
		"JOIN splatoon_3_vs_sub_weapon subWeapon on subWeapon.id = rtp.weapon.subWeapon.id " +
		"JOIN splatoon_3_vs_result result on result.id = rtp.resultId " +
		"WHERE subWeapon.id = :subWeaponId and rtp.isMyself = true and result.ownJudgement in ('WIN', 'LOSE') " +
		"GROUP BY subWeapon.id")
	List<OwnUsedWeaponStatsWithWeapon> getWeaponResultStats(@Param("subWeaponId") long subWeaponId);
}

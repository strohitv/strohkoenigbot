package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.WeaponPerformanceStats;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsWeaponRepository extends CrudRepository<Splatoon3VsWeapon, Long> {
	Optional<Splatoon3VsWeapon> findByApiId(String apiId);

	@Query(
		"SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.WeaponPerformanceStats(w, S3VRT.isMyTeam, COUNT(*) as total, COUNT(case when S3VRT.judgement like 'WIN' then 1 end) as wins, COUNT(case when S3VRT.judgement like 'LOSE' then 1 end) as defeats) " +
			"FROM splatoon_3_vs_weapon w " +
			"JOIN splatoon_3_vs_result_team_player S3VRTP on w = S3VRTP.weapon " +
			"JOIN splatoon_3_vs_result_team S3VRT on S3VRTP.resultId = S3VRT.resultId AND S3VRTP.teamOrder = S3VRT.teamOrder " +
			"JOIN splatoon_3_vs_result S3R on S3VRT.resultId = S3R.id " +
			"JOIN splatoon_3_vs_mode m on S3R.mode = m " +
			"WHERE (m.id != 9 AND NOT :returnPrivateBattles) AND (m.id == 9 AND :returnPrivateBattles) " +
			"AND S3VRT.judgement in ('WIN', 'LOSE') " +
			"AND S3VRTP.isMyself = :returnOwnStats " +
			"GROUP BY w, S3VRT.isMyTeam")
	List<WeaponPerformanceStats> getPerformanceStats(boolean returnOwnStats, boolean returnPrivateBattles);
}

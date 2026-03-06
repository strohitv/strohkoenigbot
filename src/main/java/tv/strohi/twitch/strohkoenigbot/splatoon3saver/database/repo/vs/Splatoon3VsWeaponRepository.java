package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import feign.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.WeaponPerformanceStats;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsWeaponRepository extends CrudRepository<Splatoon3VsWeapon, Long> {
	Optional<Splatoon3VsWeapon> findByApiId(String apiId);

	@Query("SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStatsWithWeapon(mode.name as modeName, weapon.id as weaponId, COUNT(*) as total, COUNT(case when result.ownJudgement like 'WIN' then 1 end) as wins, COUNT(case when result.ownJudgement like 'LOSE' then 1 end) as defeats) " +
		"FROM splatoon_3_vs_result_team_player rtp " +
		"JOIN splatoon_3_vs_weapon weapon on weapon.id = rtp.weapon.id " +
		"JOIN splatoon_3_vs_result result on result.id = rtp.resultId " +
		"JOIN splatoon_3_vs_mode mode on result.mode.id = mode.id " +
		"WHERE rtp.isMyself = true and result.ownJudgement in ('WIN', 'LOSE') and result.playedTime < :streamStart " +
		"GROUP BY mode.name, weapon.id")
	List<OwnUsedWeaponStatsWithWeapon> getWeaponResultStatsForAllWeapons(@Param("streamStart") Instant streamStart);

	@Query("SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.OwnUsedWeaponStats(mode.name as modeName, COUNT(*) as total, COUNT(case when result.ownJudgement like 'WIN' then 1 end) as wins, COUNT(case when result.ownJudgement like 'LOSE' then 1 end) as defeats) " +
		"FROM splatoon_3_vs_result_team_player rtp " +
		"JOIN splatoon_3_vs_weapon weapon on weapon.id = rtp.weapon.id " +
		"JOIN splatoon_3_vs_result result on result.id = rtp.resultId " +
		"JOIN splatoon_3_vs_mode mode on result.mode.id = mode.id " +
		"WHERE weapon.id = :weaponId and rtp.isMyself = true and result.ownJudgement in ('WIN', 'LOSE') " +
		"GROUP BY mode.name")
	List<OwnUsedWeaponStats> getWeaponResultStats(@Param("weaponId") long weaponId);

	@Query(
		"SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.WeaponPerformanceStats(w, S3VRT.isMyTeam, COUNT(*) as total, COUNT(case when S3VRT.judgement like 'WIN' then 1 end) as wins, COUNT(case when S3VRT.judgement like 'LOSE' then 1 end) as defeats)" +
			"\n FROM splatoon_3_vs_weapon w" +
			"\n JOIN splatoon_3_vs_result_team_player S3VRTP on w = S3VRTP.weapon" +
			"\n JOIN splatoon_3_vs_result_team S3VRT on S3VRTP.resultId = S3VRT.resultId AND S3VRTP.teamOrder = S3VRT.teamOrder" +
			"\n JOIN splatoon_3_vs_result S3R on S3VRT.resultId = S3R.id" +
			"\n JOIN splatoon_3_vs_mode m on S3R.mode = m" +
			"\n WHERE ((m.id != 9 AND NOT :returnPrivateBattles = true) OR (m.id = 9 AND :returnPrivateBattles = true))" +
			"\n AND S3VRT.judgement in ('WIN', 'LOSE')" +
			"\n AND S3VRTP.isMyself = :returnOwnStats" +
			"\n GROUP BY w, S3VRT.isMyTeam")
	List<WeaponPerformanceStats> getPerformanceStats(boolean returnOwnStats, boolean returnPrivateBattles);
}

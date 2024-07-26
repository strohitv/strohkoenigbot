package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import feign.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.ModeRuleWinCountGame;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.TeamPlayerSize;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsResultRepository extends CrudRepository<Splatoon3VsResult, Long> {
	Optional<Splatoon3VsResult> findById(long id);
	Optional<Splatoon3VsResult> findByApiId(String apiId);
	Optional<Splatoon3VsResult> findTop1ByOrderByPlayedTimeDesc();

	Optional<Splatoon3VsResult> findTopByOrderByIdDesc();

	Page<Splatoon3VsResult> findAll(Pageable pageable);
	Page<Splatoon3VsResult> findAllByOrderByIdDesc(Pageable pageable);

	@Query(value = "SELECT result.id" +
		" FROM splatoon_3_vs_result result" +
		" ORDER BY result.id desc")
	List<Long> findLatestGameIds(Pageable pageable);

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.ModeRuleWinCountGame(mode, rule, COUNT(*))" +
		" FROM splatoon_3_vs_result result" +
		" JOIN splatoon_3_vs_mode mode ON mode = result.mode" +
		" JOIN splatoon_3_vs_rule rule ON rule = result.rule" +
		" WHERE result.ownJudgement = 'WIN'" +
		" AND mode.apiMode not like '%PRIVATE%'" +
		" GROUP BY mode, rule")
	List<ModeRuleWinCountGame> findModeAndRuleWinCounts();

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.TeamPlayerSize(player.team, COUNT(player))" +
		" FROM splatoon_3_vs_result_team_player player" +
		" JOIN splatoon_3_vs_result result on result.id = player.resultId" +
		" WHERE result.mode = :mode" +
		" AND result.ownJudgement = 'WIN'" +
		" GROUP BY player.team" +
		" HAVING max(player.isMyself) = true")
	List<TeamPlayerSize> findTriColorOwnTeamWins(@Param("mode") Splatoon3VsMode mode);

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount(specialWeapon, COUNT(*))" +
		" FROM splatoon_3_vs_result_team_player player" +
		" JOIN splatoon_3_vs_weapon weapon on weapon = player.weapon" +
		" JOIN splatoon_3_vs_special_weapon specialWeapon on specialWeapon = weapon.specialWeapon" +
		" JOIN splatoon_3_vs_result result on result.id = player.resultId" +
		" WHERE player.isMyself = true" +
		" AND player.specials > 0" +
		" AND result.ownJudgement = 'WIN'" +
		" AND result.mode.apiMode not like '%PRIVATE%'" +
		" GROUP BY specialWeapon")
	List<SpecialWinCount> findSpecialWins();
}

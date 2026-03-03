package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import feign.Param;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsStage;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStats;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStatsWithRule;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsStageRepository extends CrudRepository<Splatoon3VsStage, Long> {
	Optional<Splatoon3VsStage> findById(long id);

	Optional<Splatoon3VsStage> findByApiId(String id);

	@NotNull List<Splatoon3VsStage> findAll();

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStatsWithRule(stage.name, mode.name, rule.name, COUNT(case when result.ownJudgement like 'WIN' then 1 end) as wins, COUNT(case when result.ownJudgement like 'LOSE' then 1 end) as defeats) " +
		"FROM splatoon_3_vs_result result " +
		"JOIN splatoon_3_vs_stage stage on result.stage.id = stage.id " +
		"JOIN splatoon_3_vs_mode mode on result.mode.id = mode.id " +
		"JOIN splatoon_3_vs_rule rule on result.rule.id = rule.id " +
		"WHERE result.ownJudgement like 'WIN' or result.ownJudgement like 'LOSE' " +
		"GROUP BY stage.name, mode.name, rule.name")
	List<StageWinStatsWithRule> findAllStageWinStats();

	@Query(value = "SELECT new tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.StageWinStats(stage.name as stageName, mode.name as modeName, COUNT(case when result.ownJudgement like 'WIN' then 1 end) as wins, COUNT(case when result.ownJudgement like 'LOSE' then 1 end) as defeats) " +
		"FROM splatoon_3_vs_result result " +
		"JOIN splatoon_3_vs_stage stage on result.stage.id = stage.id " +
		"JOIN splatoon_3_vs_mode mode on result.mode.id = mode.id " +
		"JOIN splatoon_3_vs_rule rule on result.rule.id = rule.id " +
		"WHERE stage.id = :stageId and rule.id = :ruleId and (result.ownJudgement like 'WIN' or result.ownJudgement like 'LOSE') " +
		"GROUP BY stage.name, mode.name")
	List<StageWinStats> findStageWinStats(@Param("stageId") long stageId, @Param("ruleId") long ruleId);
}
